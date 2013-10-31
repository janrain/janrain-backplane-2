Backplane README
================

This line is added to test the pull-triggered build for CI.  This should not be merged.

This Backplane Server implements both the [Backplane v2.0] [2] and the previous
[Backplane v1.2] [1] specifications.

Specifics for each of the Backplane versions are described in the following README files:

* [README12.md] [4]

* [README20.md] [5]


The deployment environment is Amazon AWS / EC2 and the storage engine used is Redis.

Environment Configuration
-----------------------------

The Backplane Server learns its bootstrap configuration parameters from the following environment variables:

* `ZOOKEEPER_SERVERS`: (Required) List of ZooKeeper servers, which will just be a single entry, if a ZooKeeper server is
run on each Backplane node (e.g., "localhost:2181").

* `REDIS_SERVER_PRIMARY`: (Required) Location of primary Redis server (e.g., "redis1.databaseserver.com:6379").

* `REDIS_SERVER_READS`: (Required) Location of Redis server for reads [may be the same as the primary] (e.g., "localhost:6379").

* `AWS_INSTANCE_ID` : (Required) Used for logging (e.g., "AWS_INSTANCE_ID=BACKPLANE").

* `GRAPHITE_SERVER`: (Optional) Destination for Backplane metrics (e.g., "graphite.reporting.com:2003").

* `IP_WHITE_LIST`: (Optional) Allowed IP addresses to access the /backplane_metrics endpoint (e.g., "123.123.123.123, 234.234.234.234").
Localhost is allowed as default.


Backplane Server Configuration
------------------------------

* `DEBUG_MODE`: boolean flag for debug logging and behavior

* `CONFIG_CACHE_AGE_SECONDS`: how long to keep this server configuration data in a memory cache

* `CLEANUP_INTERVAL_MINUTES`: how often to run a thread for cleaning up expired items,
such as messages, tokens, sessions, etc.

* `DEFAULT_MESSAGES_MAX`: the default maximum number of messages in a Backplane channel,
if not explicitly configured for the channel's bus

Administrator Authentication
----------------------------

Administrators can access the Bus Provisioning API and update Bus Configuration data.

To add the admin user account on initial deployment, use the /admin web interface.

* `USER`: admin username

* `PWDHASH`: admin password (hash)


Building From Source
--------------------

This project requires the [Maven] [3] build tool.

        mvn package -DskipTests=true

To process the tests, you must start the Redis and ZooKeeper instances.

Use the following modification to the Maven build command (be sure to add your parameters):

        mvn package -DargLine="AWS_INSTANCE_ID=test -DZOOKEEPER_SERVERS=localhost:2181 -DREDIS_SERVER_PRIMARY=localhost:6379 -DREDIS_SERVER_READS=localhost:6379"

Maven will create a WAR file for deployment in the `/target/` directory.


[1]: http://sites.google.com/site/backplanespec/documentation/backplane1-2
[2]: http://sites.google.com/site/backplanespec/documentation/backplane2-0-draft08
[3]: http://maven.apache.org/guides/getting-started/maven-in-five-minutes.html
[4]: http://github.com/janrain/janrain-backplane-2/blob/master/README12.md "Backplane v1.2"
[5]: http://github.com/janrain/janrain-backplane-2/blob/master/README20.md "Backplane v2.0"
[6]: http://typica.googlecode.com/files/sdbShell.jar

