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
        if (Backplane.getChannelID()) {
            fn();
        } else {
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
    };
    BP.warn = function(msg) {
        if (window.console && window.console.warn) {
            console.warn("Backplane WARNING: " + msg);
        }
    };
    BP.error = function(msg) {
        if (window.console && window.console.error) {
            console.error("Backplane ERROR: " + msg);
        }
    };
    BP.version = "2.0.6";
    BP.token = null;
    BP.refresh_token = null;
    BP.channelName = null;
    BP.block = 0;
    BP.config = {};
    BP.runRequests = false;
    BP.firstFrameReceived = false;
    BP.replayOnPageLoad = false;
    BP.memoryCachedMessages = {};
    BP.memoryCachedMessagesIndex = [];
    BP.cacheMax = 10;
    BP.subscribers = {};
    BP.messageCacheIndexKey = "backplane2CachedMessagesIndex";
    BP.messageCacheKey = "backplane2CachedMessages";
    BP.messageCacheExpires = "backplane2CacheExpires";
    BP.timers = {};
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
 *     cacheMax (optional) - how many messages to cache for late arriving widgets, default is 10
 *     block (optional) - how many seconds to hold connection open with server
 *     replayOnPageLoad (optional) - replay all messages from long term cache on each page load, default is false
 */
Backplane.init = function(config) {
    this.log("initializing");
    config = config || {};
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
    this.replayOnPageLoad = config.replayOnPageLoad || this.replayOnPageLoad;
    this.block = config.block || 0;
    if (typeof this.config.channelExpires === "undefined") {
        var d = new Date();
        d.setFullYear(d.getFullYear() + 5);
        this.config.channelExpires = d.toGMTString();
    }

    if (this.getChannelName()) {
        this.syncMemoryCache();
        this.onInit();
        this.request();
    } else {
        this.invalidateCache();
        this.fetchNewChannel();
    }
    return true;
};


/**
 * Subscribes to messages from Backplane server
 *
 * @param {Function} Callback - Callback function which accepts backplane messages
 * @param {Boolean} - true if the clients wants all messages replayed on page reload
 * @returns Subscription ID which can be used later for unsubscribing
 */
Backplane.subscribe = function(callback, replaysWanted) {
    if (!this.getChannelName()) {
        return false;
    }

    if(typeof(replaysWanted)==='undefined') replaysWanted = this.replayOnPageLoad;

    if (!this.checkSubscribers()) {
        // No subscribers means no request loop is running;
        // start one up.
        this.runRequests = true;
        this.request();
    }
    var i, id = new Date().getTime() + Math.random();
    var subscriber = {
      id: id,
      callback: callback,
      wantsReplays:  replaysWanted
    };
    this.subscribers[id] = subscriber;
    // If the first frame has already been processed 
    // or if replayOnPageLoad is true and the subscriber wants replays
    // push all messages to subscriber from memory cache 
    if (this.firstFrameReceived || subscriber.wantsReplays) {
        for (i=0; i<this.memoryCachedMessagesIndex.length; i++) {
            subscriber.callback(this.memoryCachedMessages[this.memoryCachedMessagesIndex[i]]);
        }
    }
    return id;
};

/**
 * Retrieve all long term cached messages.
 *
 */
Backplane.getCachedMessages = function() {
    var indexString, messageString, cachedIndex, message, expireDate, messages = [], messagesIndex = [], cached = {}, i;
    if (window.localStorage) {
        indexString = window.localStorage.getItem(this.messageCacheIndexKey);
        messageString = window.localStorage.getItem(this.messageCacheKey);
        if (indexString && messageString) {
            try {
                cachedIndex = JSON.parse(indexString);
                cached = JSON.parse(messageString);
                for (i = 0; i < cachedIndex.length; i++) {
                    // only add if the message is NOT expired
                    message = cached[cachedIndex[i]];
                    expireDate = new Date(message.expire);
                    if (expireDate > new Date()) {
                        messages.push(message);
                        messagesIndex.push(cachedIndex[i]);
                    } else {
                        delete cached[cachedIndex[i]];
                    }
                }
            } catch(e) {
                // if we get a parse error, let's just write back empty
                this.error("failed to parse cached message data");

            }
            // update localStorage to remove any expired messages
            window.localStorage.setItem(this.messageCacheIndexKey, JSON.stringify(messagesIndex));
            window.localStorage.setItem(this.messageCacheKey, JSON.stringify(cached));
        }
    }
    return messages;
};


Backplane.syncMemoryCache = function() {
    this.memoryCachedMessages = {};
    this.memoryCachedMessagesIndex = [];
    var messages = Backplane.getCachedMessages();
    for (i = 0; i < messages.length; i++) {
       Backplane.addMessageToMemoryCache(messages[i]);
    } 
}

Backplane.addMessageToMemoryCache = function(message) {
   if (!this.memoryCachedMessages.hasOwnProperty(message.messageURL)) {
       this.memoryCachedMessages[message.messageURL] = message;
       this.memoryCachedMessagesIndex.push(message.messageURL);
   }
   if (this.memoryCachedMessagesIndex.length > this.cacheMax) {
      delete this.memoryCachedMessages[this.memoryCachedMessagesIndex[0]];
      this.memoryCachedMessagesIndex.splice(0,1);
   }
}

Backplane.addMessageToLongTermCache = function(message) {
    var indexString, messageString, cachedIndex = [], cached = {};
 
    if (window.localStorage) {
        indexString = window.localStorage.getItem(this.messageCacheIndexKey);
        messageString = window.localStorage.getItem(this.messageCacheKey);
        if (indexString && messageString) {
            try {
                cachedIndex = JSON.parse(indexString);
                cached = JSON.parse(messageString);
            } catch(e) {
                this.error("failed to parse cached message data");
            }

        }
        cached[message.messageURL] = message;
        if (!Backplane.inArray(cachedIndex, message.messageURL)) {
            cachedIndex.push(message.messageURL);
        }
        // sort, just in case
        cachedIndex.sort();
        // update localStorage to remove any expired messages
        window.localStorage.setItem(this.messageCacheIndexKey, JSON.stringify(cachedIndex));
        window.localStorage.setItem(this.messageCacheKey, JSON.stringify(cached));
    }  // what about IE7?

}

Backplane.inArray = function in_array(array, id) {
    for(var i=0;i<array.length;i++) {
        if(array[i] === id) {
            return true;
        }
    }
    return false;
}

/**
 * Removes specified subscription
 *
 * @param {Integer} Subscription ID
 */
Backplane.unsubscribe = function(subscriptionID) {
    if (!subscriptionID) { return false; }
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
    if (!this.getChannelName()) { return false; }
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
    if (!this.getChannelName() || !interval) { return false; }
    this.awaiting.since = this.getTS();
    this.awaiting.interval = interval;
    // we should wait entire interval if no types were specified
    this.awaiting.nonstop = !types;
    if (types) {
        types = typeof types === "string" ? [types] : types;
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

    var scopes, k, channel;
    this.log("received access token and channel from server");
    if (initPayload.error != null) {
      this.error("Error initializing backlane token: " + initPayload.error);
      return;
    }
    this.token = initPayload.access_token;
    this.refresh_token = initPayload.refresh_token;
    scopes = initPayload.scope.split(" ");
    for (k = 0; k < scopes.length; k++) {
        if (scopes[k].indexOf("channel:") > -1) {
            channel = scopes[k].split(":");
            this.channelName = channel[1];
            this.log("channel set to: " + this.channelName);
        }
    }

    if (this.channelName === null) {
        this.error("No channel found in the returned scope");
        return;
    }

    this.setCookieChannel();
    this.channelID = this.generateChannelID();
    this.log("channelID = " + this.channelID);
    this.subscribers = {};
    this.onInit();
    this.fetchMessages();
};

Backplane.generateNextFrameURL = function() {
    if (typeof this.since === "undefined") {
        this.since = this.config.serverBaseURL + "/messages";
    }
    var localBlock, localSince = this.since;
    if (localSince.indexOf('?') > -1 )  {
        localSince += "&";
    } else {
        localSince += "?";
    }
    localBlock = this.block;

    return localSince + "callback=Backplane.response&access_token=" + this.token + "&block=" + localBlock + "&rnd=" + Math.random();
};

Backplane.generateChannelID = function() {
    return this.config.serverBaseURL + "/bus/" + this.config.busName + "/channel/" + this.channelName;
};

Backplane.getChannelName = function() {
    if (this.channelName === null) { return false; }
    return this.channelName;
};

Backplane.getTS = function() {
    return Math.round(new Date().getTime() / 1000);
};

Backplane.checkSubscribers = function() {
    var name;
    for (name in this.subscribers) {
        if (this.subscribers.hasOwnProperty(name)) {
            return true;
        }
    }
    return false;
};

Backplane.loadChannelFromCookie = function() {
    var parts, match;

    match = (document.cookie || "").match(/backplane2-channel=([\w\W]*?)(?:$|;)/);
    if (!match || !match[1]) { return {}; }
    parts = match[1].split(":");
    this.token = decodeURIComponent(parts[0]);
    this.channelName = decodeURIComponent(parts[1]);
    // XXX: migration: the refresh token was not saved by older
    // versions of this code (remove this check eventually).
    if (typeof parts[2] !== "undefined") {
        this.log("found refresh token in cookie");
        this.refresh_token = decodeURIComponent(parts[2]);
    }
    if (this.token.length < 20 || this.channelName.length < 30 ||
        (typeof parts[2] !== "undefined" && this.refresh_token.length < 20)) {
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
    this.invalidateCache();
    this.channelName = null;
    this.token = null;
    this.refresh_token = null;
    this.setCookieChannel();
    // make the async call to retrieve a server generated channel
    this.fetchNewChannel();
};

Backplane.makeCall = function(url, type, charset) {
    var id = type + "Id", oldScript, prop, script, firstScript;
    oldScript = document.getElementById(id);
    while (oldScript) {
        oldScript.parentNode.removeChild(oldScript);
        for (prop in oldScript) {
            delete oldScript[prop];
        }
        oldScript = document.getElementById(id);
    }

    script = document.createElement("script");
    script.type = "text/javascript";
    script.id = id;
    if (typeof charset !== "undefined") {
        script.charset = charset;
    }
    script.src = url;
    firstScript = document.getElementsByTagName("script")[0];
    firstScript.parentNode.insertBefore(script, firstScript);
};

Backplane.fetchNewChannel = function() {
    var url = this.config.serverBaseURL + "/token?callback=Backplane.finishInit&bus=" + this.config.busName + "&rnd=" + Math.random();
    Backplane.makeCall(url, "fetchNewChannel");
};

Backplane.invalidateCache = function() {
    this.log("removing cached backplane messages");
    this.memoryCachedMessages = {};
    this.memoryCachedMessagesIndex = [];

    if (window.localStorage) {
        window.localStorage.removeItem(this.messageCacheExpires);
        window.localStorage.removeItem(this.messageCacheKey);
        window.localStorage.removeItem(this.messageCacheIndexKey);
    }
};

Backplane.refreshToken = function() {
    var url = this.config.serverBaseURL + "/token?callback=Backplane.refreshTokenResponse&refresh_token=" + this.refresh_token + "&rnd=" + Math.random();
    this.makeCall(url, "refreshToken");
};

/**
 * Callback function for token refresh request
 * @param tokenPayload in the form {"token_type":"Bearer","access_token":"AAWBuJ1H2OjNvfK2oJXyXh",
 *      "expires_in":3600,"scope":"bus:test-bus channel:XUw4aR074KGMit1lHepKOKAQZtBwxSUt",
 *      "refresh_token":"ARXU21NC8gLdKwIM6SZIf0"}
 */
Backplane.refreshTokenResponse = function(tokenPayload) {
    if (typeof tokenPayload.error_description !== "undefined") {
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
};

Backplane.normalizeURL = function(rawURL) {
    return rawURL.replace(/^\s*(https?:\/\/)?([\w\W]*?)[\s\/]*$/, function(match, proto, uri){
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
    var timeout, ts = this.getTS(), relative, limit;

    if (ts < this.awaiting.until) {
        // stop frequent polling as soon as all the necessary messages received
        if (!this.awaiting.nonstop && !this.awaiting.queue.length) {
            this.awaiting.until = ts;
            return this.calcTimeout();
        }
        relative = ts - this.awaiting.since;
        limit = this.intervals.frequent - this.intervals.min;
        // we should reach this.intervals.frequent at the end
        timeout = this.intervals.min +
            Math.round(limit * relative / this.awaiting.interval);
    } else if (ts < this.awaiting.until + this.intervals.slowdown) {
        relative = ts - this.awaiting.until;
        limit = this.intervals.regular - this.intervals.frequent;
        // we should reach this.intervals.regular at the end
        timeout = this.intervals.frequent +
            Math.round(limit * relative / this.intervals.slowdown);
    } else {
        timeout = typeof this.since === "undefined" ? 0 : this.intervals.regular;
        this.awaiting.nonstop = false;
    }
    return (timeout * 1000);
};

Backplane.fetchMessages = function() {
    var url = Backplane.generateNextFrameURL();
    Backplane.makeCall(url, "fetchMessages", "utf-8");
};

Backplane.convertMessageURLtoNextURL = function(messageURL, serverBaseURL) {
    var baseURL = this.config.serverBaseURL || serverBaseURL;
    // convert "https://my.backplaneserver.com/v2/message/2013-03-21T22:34:01.867Z-uDuDFMjcAw"
    // to "https://my.backplaneserver.com/v2/messages?since=2013-03-21T22:34:01.867Z-uDuDFMjcAw"
    return messageURL.replace(baseURL + "/message/",  baseURL + "/messages?since=");
};

Backplane.request = function() {
    var self = this;
    if (!this.getChannelName() || !this.runRequests) { return false; }
    this.stopTimer("regular");
    this.stopTimer("watchdog");
    this.timers.regular = setTimeout(function() {
        // if no response in the reasonable time just restart request
        self.timers.watchdog = setTimeout(function() {
            self.request();
        }, (Backplane.block * 1000) + 5000);

        // If no 'since' parameter exists, this is the FIRST call.
        // Check the long term cache to get the last message id delivered 
        // and sync up the in-memory message header cache if required.
        if (!self.since) {
            var messages = self.getCachedMessages();
            self.log(messages.length + " message(s) in cache");
            if (messages.length > 0) {
                self.since = self.convertMessageURLtoNextURL(messages[messages.length-1].messageURL);
            }
            // if the desire is to play back all messages for each page load
            // then sync up the memory cache with the long term cache
            if (self.replayOnPageLoad) {
               Backplane.syncMemoryCache();
            }            
        }

        Backplane.fetchMessages();
    }, this.calcTimeout());
};


/**
 * Callback function for message frame request
 * @param messages
 */

Backplane.response = function(messageFrame) {
    var self = this, i, j, k, l, satisfied, queue;
    this.stopTimer("watchdog");

    if (typeof messageFrame.error_description !== "undefined") {
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

    //"nextURL":"https://backplane1.janrainbackplane.com/v2/messages?since=2013-03-21T22:37:52.001Z-bhVE4nDhYM"
    this.since = messageFrame.nextURL;

    for (i = 0; i < messageFrame.messages.length; i++) {

        // store in both caches
        Backplane.addMessageToLongTermCache(messageFrame.messages[i]);
        Backplane.addMessageToMemoryCache(messageFrame.messages[i]);

        // notify subscribers
        for (j in this.subscribers) {
            if (this.subscribers.hasOwnProperty(j)) {
                this.subscribers[j].callback(messageFrame.messages[i]);
            }
        }

        // clean up awaiting specific events queue
        queue = [];
        for (k = 0; k < this.awaiting.queue.length; k++) {
            satisfied = false;
            for (l = 0; l < this.awaiting.queue[k].length; l++) {
                if (this.awaiting.queue[k][l] === messageFrame.messages[i].type) {
                    satisfied = true;
                }
            }
            if (!satisfied) {
                queue.push(this.awaiting.queue[k]);
            }
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
};

Backplane.stopTimer = function(name) {
    var timer = this.timers[name];
    if (timer) {
        clearTimeout(timer);
    }
};
