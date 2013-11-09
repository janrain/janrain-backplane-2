organization := "com.janrain"

name := "backplane-server"

version := "2.0"

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-unchecked", "-deprecation")

// XXX: Compile in debug mode, otherwise spring throws exceptions
// http://www.objectpartners.com/2010/08/12/spring-pathvariable-head-slapper/
javacOptions += "-g"

javaOptions in Test := Seq(
  "-DAWS_INSTANCE_ID=bp2local",
  "-DREDIS_SERVER_PRIMARY=localhost:6379",
  "-DREDIS_SERVER_READS=localhost:6379",
  "-DZOOKEEPER_SERVERS=localhost:2181"
)

fork in Test := true

testListeners <+= target map (t => new com.dadrox.sbt.test.reports.Xml(t getName))

webSettings

seq(jsSettings : _*)

(compile in Compile) <<= compile in Compile dependsOn (JsKeys.js in Compile)

(JsKeys.compilationLevel in (Compile, JsKeys.js)) := CompilationLevel.SIMPLE_OPTIMIZATIONS

(sourceDirectory in (Compile, JsKeys.js)) <<= (sourceDirectory in Compile)(_ / "javascript")

(resourceManaged in (Compile, JsKeys.js)) <<= (resourceManaged in Compile)(_ / "static")

// add managed resources to the webapp
(webappResources in Compile) <+= (resourceManaged in Compile)

// A hack to set context path for jetty to "/backplane-server"
env in Compile := Some(file(".") / "sbt-jetty-env.xml" asFile)

net.virtualvoid.sbt.graph.Plugin.graphSettings

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-library" % "2.10.2",
  // javax & servlet
  "javax.inject" % "javax.inject" % "1",
  "javax.mail" % "mail" % "1.4.7" force(),
  "javax.servlet" % "servlet-api" % "2.5" % "provided",
  "javax.servlet.jsp" % "jsp-api" % "2.1" % "provided",
  "javax.servlet" % "jstl" % "1.2",
  // for UriBuilder utility class
  "javax.ws.rs" % "jsr311-api" % "1.1.1",
  "com.sun.jersey" % "jersey-client" % "1.4",
  // Spring
  ("org.springframework" % "spring-context" % "3.0.6.RELEASE")
    .exclude("commons-logging", "commons-logging"),
  "org.springframework" % "spring-webmvc" % "3.0.6.RELEASE",
  // Logging
  "org.slf4j" % "slf4j-api" % "1.5.10",
  "org.slf4j" % "slf4j-log4j12" % "1.5.10" % "runtime",
  "org.slf4j" % "jcl-over-slf4j" % "1.5.10" % "runtime",
  "log4j" % "log4j" % "1.2.16",
  "avalon-framework" % "avalon-framework" % "4.1.3",
  // JSR 303 with Hibernate Validator
  "javax.validation" % "validation-api" % "1.0.0.GA",
  ("org.hibernate" % "hibernate-validator" % "4.0.2.GA")
//    .exclude("com.sun.xml.bind", "jaxb-impl")
    .exclude("org.slf4j", "slf4j-api"),
  // URL Rewrite
  "org.tuckey" % "urlrewritefilter" % "3.1.0",
  // joda time
  "joda-time" % "joda-time" % "1.6.2",
  // Apache commons
  "commons-lang" % "commons-lang" % "2.5",
  "commons-codec" % "commons-codec" % "1.4",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  // JSON parser
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.6.0" force(),
  "org.codehaus.jackson" % 	"jackson-core-asl" % "1.6.0" force(),
  // Test
  "junit" % "junit" % "4.4" % "test",
  "com.novocode" % "junit-interface" % "0.10" % "test",
  "org.powermock.modules" % "powermock-module-junit4" % "1.4.5" % "test",
  "org.powermock.api" %	"powermock-api-easymock" % "1.4.5" % "test",
  "org.easymock" % "easymock" % "3.0" % "test",
  "org.springframework" % "spring-mock" % "2.0.8" % "test",
  "org.springframework" % "spring-test" % "3.0.6.RELEASE" % "test",
  "org.apache.tomcat" % "catalina" % "6.0.18" % "test",
  "org.apache.xbean" % "xbean-spring" % "3.7",
  ("org.apache.velocity" % "velocity" % "1.7")
    .exclude("commons-collections","commons-collections"),
  ("com.amazonaws" % "aws-java-sdk" % "1.2.9")
    .exclude("org.codehaus.jackson", "jackson-core-asl")
    .exclude("org.codehaus.jackson", "jackson-mapper-asl"),
  // metrics
  ("com.yammer.metrics" % "metrics-core" % "2.1.2")
    .exclude("org.slf4j", "slf4j-api"),
  ("com.yammer.metrics" % "metrics-servlet" % "2.1.2")
    .exclude("org.slf4j", "slf4j-api")
    .exclude("org.codehaus.jackson", "jackson-core-asl")
    .exclude("org.codehaus.jackson", "jackson-mapper-asl"),
  ("com.yammer.metrics" % "metrics-log4j" % "2.1.2")
    .exclude("org.slf4j", "slf4j-api"),
  ("com.yammer.metrics" % "metrics-graphite" % "2.1.2")
    .exclude("org.slf4j", "slf4j-api"),
  // legacy serialization / supersimpledb
  ("com.janrain.commons.supersimpledb" % "commons-supersimpledb" % "1.0.27")
    .exclude("org.codehaus.jackson", "jackson-core-asl")
    .exclude("org.codehaus.jackson", "jackson-mapper-asl"),
  // intellij annotations library for @NotNull and @Nullable
  "org.kohsuke.jetbrains" % "annotations" % "9.0",
  ("net.sf.ehcache" % "ehcache" % "2.5.2")
    .exclude("org.slf4j", "slf4j-api"),
  // Redis
  ("net.debasishg" % "redisclient_2.10" % "2.10")
    .exclude("org.slf4j", "slf4j-api"),
  "redis.clients" % "jedis" % "2.1.0.a",
  ("com.netflix.curator" % "curator-recipes" % "1.1.15")
    .exclude("org.slf4j", "slf4j-api")
    .exclude("org.slf4j", "slf4j-log4j12"),
  // Analytics (flume, akka, scalax, etc.)
  "com.github.scala-incubator.io" % "scala-io-file_2.10" % "0.4.2",
  "com.typesafe.akka" % "akka-actor_2.10" % "2.1.1",
  ("com.cloudera" % "flume-log4j-appender" % "0.9.4-cdh3u3")
    .exclude("javax.servlet.jsp", "jsp-api")
    .exclude("javax.servlet", "servlet-api")
    .exclude("org.mortbay.jetty", "servlet-api")
    .exclude("org.slf4j", "slf4j-api")
    .exclude("org.slf4j", "slf4j-log4j12")
    .exclude("org.codehaus.jackson", "jackson-core-asl")
    .exclude("org.codehaus.jackson", "jackson-mapper-asl")
    .exclude("antlr", "antlr")
    .exclude("com.sun.xml.bind", "jaxb-impl"),
  ("com.cloudera" % "flume-core" % "0.9.4-cdh3u3")
    .exclude("javax.servlet.jsp", "jsp-api")
    .exclude("javax.servlet", "servlet-api")
    .exclude("org.mortbay.jetty", "servlet-api")
    .exclude("org.slf4j", "slf4j-api")
    .exclude("org.slf4j", "slf4j-log4j12")
    .exclude("org.codehaus.jackson", "jackson-core-asl")
    .exclude("org.codehaus.jackson", "jackson-mapper-asl")
    .exclude("antlr", "antlr")
    .exclude("com.sun.xml.bind", "jaxb-impl"),
  ("org.apache.avro" % "avro-ipc" % "1.5.4")
    .exclude("org.mortbay.jetty", "servlet-api")
    .exclude("org.slf4j", "slf4j-api")
    .exclude("org.codehaus.jackson", "jackson-core-asl")
    .exclude("org.codehaus.jackson", "jackson-mapper-asl")
    .exclude("commons-collections","commons-collections")
    .exclude("org.mortbay.jetty", "servlet-api"),
  // For sbt web plugin
  "org.eclipse.jetty" % "jetty-server" % "7.2.2.v20101205" % "container",
  "org.eclipse.jetty" % "jetty-webapp" % "7.2.2.v20101205" % "container",
  "org.eclipse.jetty" % "jetty-jsp-2.1" % "7.2.2.v20101205" % "container",
  "org.eclipse.jetty" % "jetty-plus" % "7.2.2.v20101205" % "container",
  "org.mortbay.jetty" % "jsp-2.1-glassfish" % "2.1.v20100127" % "container"
)

resolvers ++= Seq(
  // For Hibernate Validator
  "JBoss Maven Release Repository" at "https://repository.jboss.org/nexus/content/repositories/releases",
  // Test tools respositories
  "Powermock Repo" at "http://powermock.googlecode.com/svn/repo/",
  "Java.net Repository for Maven" at "http://download.java.net/maven/2/",
  // Coda Hale's Metrics repo
  "repo.codahale.com" at "http://repo.codahale.com",
  // Janrain's dependencies
  "janrain-repo" at "https://repository-janrain.forge.cloudbees.com/release",
  "Spy Repository" at "http://files.couchbase.com/maven2/",
  "codehaus-release" at "http://repository.codehaus.org",
  // For the ehcache dependencies
  "cloudera" at "https://repository.cloudera.com/artifactory/cloudera-repos/"
)
