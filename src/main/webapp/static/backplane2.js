/*
 * Copyright (c) 2006-2011 Echo <solutions@aboutecho.com>. All rights reserved.
 * You may copy and modify this script as long as the above copyright notice,
 * this condition and the following disclaimer is left intact.
 * This software is provided by the author "AS IS" and no warranties are
 * implied, including fitness for a particular purpose. In no event shall
 * the author be liable for any damages arising in any way out of the use
 * of this software, even if advised of the possibility of such damage.
 * $Id: backplane.js 32046 2011-03-31 08:53:15Z jskit $
 */

window.Backplane = window.Backplane || (function() {
    // Backplane is a function that accepts a function to be run onInit
    var BP = function(fn) {
        if (Backplane.getChannelID()) fn();
        else {
            Backplane.onInit = (function() {
                var original_onInit = Backplane.onInit;
                return function() {
                    original_onInit();
                    fn();
                };
            })();
        }
    };
    BP.log = function(msg) {
        if (window.console && window.console.log) {
            console.log("Backplane: " + msg);
        }
    }
    BP.warn = function(msg) {
        if (window.console && window.console.warn) {
            console.warn("Backplane WARNING: " + msg)
        }
    }
    BP.error = function(msg) {
        if (window.console && window.console.error) {
            console.error("Backplane ERROR: " + msg);
        }
    }
    BP.version = "2.0.2";
    BP.token = null;
    BP.refresh_token = null;
    BP.channelName = null;
    BP.block = 0;
    BP.config = {};
    BP.initialized = false;
    BP.runRequests = false;
    BP.firstFrameReceived = false;
    BP.cachedMessages = {};
    BP.cachedMessagesIndex = [];
    BP.cacheMax = 0;
    BP.subscribers = {};
    BP.awaiting = {
        "since": 0,
        "until": 0,
        "queue": []
    };
    BP.intervals = {
        "min": 1,
        "frequent": 5,
        "regular": 20,
        "slowdown": 120
    };
    BP.onInit = function() {};
    BP.log("backplane2.js loaded");
    return BP;
})();

/**
 * Initializes the Backplane 2 library
 *
 * @param {Object} config - Hash with configuration parameters.
 *   Possible hash keys:
 *     serverBaseURL (required) - Base URL of Backplane Server
 *     busName (required) - Customer's backplane bus name
 *     channelExpires (optional) - set backplane2-channel cookie life span
 *     initFrameFilter (optional) - function to filter the first message frame
 *     cacheMax (optional) - how many messages to cache for late arriving widgets
 *     block (optional) - how many seconds to hold connection open with server
 */
Backplane.init = function(config) {
    this.log("initializing");
    config = config || {};
    if (this.initialized) {
        this.log("library already initialized");
        return false;
    }
    if (!config.serverBaseURL) {
        this.error("must supply serverBaseURL");
        return false;
    }
    if (!config.busName) {
        this.error("must supply busName");
        return false;
    }

    this.timers = {};
    this.config = config;
    this.config.serverBaseURL = this.normalizeURL(config.serverBaseURL);

    if (this.config.serverBaseURL.indexOf("/v2") < 0) {
        this.error("serverBaseURL must include '/v2'");
        return false;
    }

    this.loadChannelFromCookie();
    
    this.cacheMax = config.cacheMax || this.cacheMax;
    this.block = config.block || 0;
    if (typeof this.config.channelExpires == "undefined") {
        var d = new Date();
        d.setFullYear(d.getFullYear() + 5);
        this.config.channelExpires = d.toGMTString();
    }

    if (this.getChannelName()) {
        this.onInit();
        //this.request();
        this.fetchMessages();
    } else {
        this.fetchNewChannel();
    }
    return true;
};


/**
 * Subscribes to messages from Backplane server
 *
 * @param {Function} Callback - Callback function which accepts backplane messages
 * @returns Subscription ID which can be used later for unsubscribing
 */
Backplane.subscribe = function(callback) {
    if (!this.initialized) return false;
    if (!this.checkSubscribers()) {
        // No subscribers means no request loop is running;
        // start one up.
        this.runRequests = true;
        this.request();
    }
    var id = (new Date()).valueOf() + Math.random();
    this.subscribers[id] = callback;
    //if the first frame has already been processed, catch this widget up
    if (this.firstFrameReceived) {
        for (var i=0; i<this.cachedMessagesIndex.length; i++) {
            callback(this.cachedMessages[this.cachedMessagesIndex[i]]);
        }
    }
    return id;
};

/**
 * Removes specified subscription
 *
 * @param {Integer} Subscription ID
 */
Backplane.unsubscribe = function(subscriptionID) {
    if (!this.initialized || !subscriptionID) return false;
    delete this.subscribers[subscriptionID];
    if (!this.checkSubscribers()) {
        // No more subscribers left; go to sleep.
        this.runRequests = false;
    }
};

/**
 * Returns channel ID (like http://backplane.customer.com/v2/bus/customer.com/channel/8ec92f459fa70b0da1a40e8fe70a0bc8)
 *
 * @returns Backplane channel ID
 */
Backplane.getChannelID = function() {
    if (!this.initialized) return false;
    return this.channelID;
};

/**
 * Notifies Backplane library about the fact that subscribers are going
 * to receive backplane messages of any of the specified types
 *
 * @param {Array} List of expected Backplane message types
 */
Backplane.expectMessages = function(types) {
    this.expectMessagesWithin(60, types);
};

/**
 * Notifies backplane library about the fact that subscribers are going
 * to receive backplane messages within specified time interval.
 *
 * @param {Integer} TimeInterval Time interval in seconds
 */
Backplane.expectMessagesWithin = function(interval, types) {
    if (!this.initialized || !interval) return false;
    this.awaiting.since = this.getTS();
    this.awaiting.interval = interval;
    // we should wait entire interval if no types were specified
    this.awaiting.nonstop = !types;
    if (types) {
        types = typeof types == "string" ? [types] : types;
        this.awaiting.queue.push(types);
    }
    var until = this.awaiting.since + interval;
    if (until > this.awaiting.until) {
        this.awaiting.until = until;
    }
    this.request();
};

/**
 * Internal functions
 *
 */

/**
 * Init callback function
 * @param initPayload in the form {"token_type":"Bearer","access_token":"AAWBuJ1H2OjNvfK2oJXyXh",
 *      "expires_in":3600,"scope":"bus:test-bus channel:XUw4aR074KGMit1lHepKOKAQZtBwxSUt",
 *      "refresh_token":"ARXU21NC8gLdKwIM6SZIf0"}
 */
Backplane.finishInit = function (initPayload) {

    this.log("received access token and channel from server");
    this.token = initPayload.access_token;
    this.refresh_token = initPayload.refresh_token;
    var scopes = initPayload.scope.split(" ");
    for (var k = 0; k < scopes.length; k++) {
        if (scopes[k].indexOf("channel:") > -1) {
            var channel = scopes[k].split(":");
            this.channelName = channel[1];
            this.log("channel set to: " + this.channelName);
        }
    }

    if (this.channelName == null) {
        this.error("No channel found in the returned scope");
    }

    this.setCookieChannel();
    this.channelID = this.generateChannelID();
    this.initialized = true;
    this.log("channelID = " + this.channelID);
    this.onInit();
    //this.request();
    this.fetchMessages();
};

Backplane.generateNextFrameURL = function() {
    if (typeof this.since == "undefined") {
        this.since = this.config.serverBaseURL + "/messages";
    }
    var localSince = this.since;
    if (localSince.indexOf('?') > -1 )  {
        localSince += "&";
    } else {
        localSince += "?";
    }
    var localBlock = this.block;
    // do not block if this is the first request for Backplane server
    if (!this.firstFrameReceived) {
        localBlock = 0;
    }

    return localSince + "callback=Backplane.response&access_token=" + this.token + "&block=" + localBlock + "&rnd=" + Math.random();
};

Backplane.generateChannelID = function() {
    return this.config.serverBaseURL + "/bus/" + this.config.busName + "/channel/" + this.channelName;
};

Backplane.getChannelName = function() {
    if (!this.initialized) return false;
    if (this.channelName == null) return false;
    return this.channelName;
};

Backplane.getTS = function() {
    return Math.round((new Date()).valueOf() / 1000);
};

Backplane.checkSubscribers = function() {
	var name;
	for (name in this.subscribers) {
		return true;
	}
	return false;
};

Backplane.loadChannelFromCookie = function() {
    var match = (document.cookie || "").match(/backplane2-channel=(.*?)(?:$|;)/);
    if (!match || !match[1]) return {};
    var parts = match[1].split(":");
    this.token = decodeURIComponent(parts[0]);
    this.channelName = decodeURIComponent(parts[1]);
    // XXX: migration: the refresh token was not saved by older
    // versions of this code (remove this check eventually).
    if (typeof parts[2] != "undefined") {
        this.log("found refresh token in cookie")
        this.refresh_token = decodeURIComponent(parts[2]);
    }
    if (this.token.length < 20 || this.channelName.length < 30 ||
      typeof parts[2] != "undefined" && this.refresh_token.length < 20) {
        this.error("backplane2-channel value: '" + match[1] + "' is corrupt");
    } else {
        this.log("retrieved token and channel from cookie");
        this.channelID = this.generateChannelID();
        this.log("channelID = " + this.channelID);
    }
};

Backplane.setCookieChannel = function() {
    document.cookie = "backplane2-channel=" + encodeURIComponent(this.token) + ":" +
        encodeURIComponent(this.channelName) + ":" + encodeURIComponent(this.refresh_token) +
        ";expires=" + this.config.channelExpires + ";path=/";
};

Backplane.resetCookieChannel = function() {
    this.channelName = null;
    this.token = null;
    this.refresh_token = null;
    this.setCookieChannel();
    // make the async call to retrieve a server generated channel
    this.fetchNewChannel();
};

Backplane.makeCall = function(url, type, charset) {
    var id = type + "Id";

    // cleanup old script if it exists to prevent memory leak
    var oldScript;
    while (oldScript = document.getElementById(id)) {
        oldScript.parentNode.removeChild(oldScript);
        for (var prop in oldScript) {
            delete oldScript[prop];
        }
    }

    var script = document.createElement("script");
    script.type = "text/javascript";
    script.id = id;
    if (typeof charset != "undefined") {
        script.charset = charset;
    }
    script.src = url;
    var firstScript = document.getElementsByTagName("script")[0];
    firstScript.parentNode.insertBefore(script, firstScript);
};

Backplane.fetchNewChannel = function() {
    var url = this.config.serverBaseURL + "/token?callback=Backplane.finishInit&bus=" + this.config.busName + "&rnd=" + Math.random();
    Backplane.makeCall(url, "fetchNewChannel");
};

Backplane.refreshToken = function() {
    var url = this.config.serverBaseURL + "/token?callback=Backplane.refreshTokenResponse&refresh_token=" + this.refresh_token + "&rnd=" + Math.random();
    Backplane.makeCall(url, "refreshToken");
};

/**
 * Callback function for token refresh request
 * @param tokenPayload in the form {"token_type":"Bearer","access_token":"AAWBuJ1H2OjNvfK2oJXyXh",
 *      "expires_in":3600,"scope":"bus:test-bus channel:XUw4aR074KGMit1lHepKOKAQZtBwxSUt",
 *      "refresh_token":"ARXU21NC8gLdKwIM6SZIf0"}
 */
Backplane.refreshTokenResponse = function(tokenPayload) {
    if (typeof tokenPayload.error_description != "undefined") {
        if (tokenPayload.error_description === "invalid token") {
            this.log("received invalid token error from server when refreshing access token; requesting new access token");
            this.resetCookieChannel();
        } else {
            this.log("received unexpected error from server when refreshing access token: " + tokenPayload.error_description);
        }
        return;
    }

    this.log("received refreshed access token from server");
    this.token = tokenPayload.access_token;
    this.refresh_token = tokenPayload.refresh_token;
    this.setCookieChannel();
}

Backplane.normalizeURL = function(rawURL) {
    return rawURL.replace(/^\s*(https?:\/\/)?(.*?)[\s\/]*$/, function(match, proto, uri){
        return (proto || window.location.protocol + "//") + uri;
    });
};

/*
 * Calculate amount of time after which the request will be sent.
 *
 * If a message is expected, we should poll more frequently, gradually
 * slowing down, until we reach the maximum interval for frequent
 * polling for expected messages (this.intervals.frequent).  After
 * that, continue to slow down until we reach the regular interval.
 */
Backplane.calcTimeout = function() {
    var timeout, ts = this.getTS();
    if (this.block > 0) {
        return 2000;
    }
    if (ts < this.awaiting.until) {
        // stop frequent polling as soon as all the necessary messages received
        if (!this.awaiting.nonstop && !this.awaiting.queue.length) {
            this.awaiting.until = ts;
            return this.calcTimeout();
        }
        var relative = ts - this.awaiting.since;
        var limit = this.intervals.frequent - this.intervals.min;
        // we should reach this.intervals.frequent at the end
        timeout = this.intervals.min +
            Math.round(limit * relative / this.awaiting.interval);
    } else if (ts < this.awaiting.until + this.intervals.slowdown) {
        var relative = ts - this.awaiting.until;
        var limit = this.intervals.regular - this.intervals.frequent;
        // we should reach this.intervals.regular at the end
        timeout = this.intervals.frequent +
            Math.round(limit * relative / this.intervals.slowdown);
    } else {
        timeout = typeof this.since == "undefined" ? 0 : this.intervals.regular;
        this.awaiting.nonstop = false;
    }
    return (timeout * 1000);
};

Backplane.fetchMessages = function() {
    var url = Backplane.generateNextFrameURL();
    Backplane.makeCall(url, "fetchMessages", "utf-8");
}

Backplane.request = function() {
    var self = this;
    if (!this.initialized || !this.runRequests) return false;
    this.stopTimer("regular");
    this.stopTimer("watchdog");
    this.timers.regular = setTimeout(function() {
        // if no response in the reasonable time just restart request
        self.timers.watchdog = setTimeout(function() {
            self.request();
        }, (Backplane.block * 1000) + 5000);
        Backplane.fetchMessages();
    }, this.calcTimeout());
};


/**
 * Callback function for message frame request
 * @param messages
 */

Backplane.response = function(messageFrame) {
    var self = this;
    this.stopTimer("watchdog");

    if (typeof messageFrame.error_description != "undefined") {
        if (messageFrame.error_description === "invalid token") {
            this.log("received invalid token error from server when retrieving messages");
            if (this.refresh_token) {
                this.log("refreshing access token");
                this.refreshToken();
            } else {
                this.log("refresh token is not available; requesting new access token");
                this.resetCookieChannel();
            }
        } else {
            this.log("received unexpected error from server when retrieving messages: " + messageFrame.error_description);
            this.log("...trying again");
        }
        // Start the request-response loop again in case the above
        // call results in a (transient) error.
        this.request();
        return;
    }

    if (this.firstFrameReceived === false) {
        if (typeof this.config.initFrameFilter != "undefined") {
            messageFrame.messages = this.config.initFrameFilter(messageFrame.messages);
        } else {
            messageFrame.messages = [];
        }
    }

    this.since = messageFrame.nextURL;

    for (var i = 0; i < messageFrame.messages.length; i++) {
        // notify subscribers
        for (var j in this.subscribers) {
            if (this.subscribers.hasOwnProperty(j)) {
                this.subscribers[j](messageFrame.messages[i]);
            }
        }
        // stash message in cache
        if (this.cacheMax > 0) {
            if (!this.cachedMessages.hasOwnProperty(messageFrame.messages[messageFrame.messages[i].messageURL])) {
                this.cachedMessages[messageFrame.messages[i].messageURL] = messageFrame.messages[i];
                this.cachedMessagesIndex.push(messageFrame.messages[i].messageURL);
            }
            if (this.cachedMessagesIndex.length > this.cacheMax) {
                delete this.cachedMessages[this.cachedMessagesIndex[0]];
                this.cachedMessagesIndex.splice(0,1);
            }
        }

        // clean up awaiting specific events queue
        var queue = [];
        for (var k = 0; k < this.awaiting.queue.length; k++) {
            var satisfied = false;
            for (var l = 0; l < this.awaiting.queue[k].length; l++) {
                if (this.awaiting.queue[k][l] == messageFrame.messages[i].type) {
                    satisfied = true;
                }
            }
            if (!satisfied) queue.push(this.awaiting.queue[k]);
        }
        this.awaiting.queue = queue;
    }

    // if the moreMessages flag is true, fetch another frame immediately
    if (messageFrame.moreMessages === true) {
        this.fetchMessages();
        return;
    }

    this.firstFrameReceived = true;
    this.request();
    //this.fetchMessages();
};

Backplane.stopTimer = function(name) {
    var timer = this.timers[name];
    if (timer) clearTimeout(timer);
};
