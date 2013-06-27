name := "backplane-server"

version := "BP_2_0.2013.26_RC1"

scalaVersion := "2.10.0"

scalacOptions += "-deprecation"

// XXX: Compile in debug mode, otherwise spring throws exceptions
javacOptions += "-g"

//javacOptions += "-Xlint:unchecked"

//compileOrder := CompileOrder.ScalaThenJava

seq(webSettings :_*)

// A hack to set context path for jetty to "/backplane-server"
env in Compile := Some(file(".") / "sbt-jetty-env.xml" asFile)

libraryDependencies ++= Seq(
  // For sbt web plugin
  //"org.mortbay.jetty" % "jetty" % "6.1.22" % "container",
  "org.eclipse.jetty" % "jetty-server" % "7.2.2.v20101205" % "container",
  "org.eclipse.jetty" % "jetty-webapp" % "7.2.2.v20101205" % "container",
  "org.eclipse.jetty" % "jetty-jsp-2.1" % "7.2.2.v20101205" % "container",
  "org.mortbay.jetty" % "jsp-2.1-glassfish" % "2.1.v20100127" % "container",
  "org.eclipse.jetty" % "jetty-plus" % "7.2.2.v20101205" % "container",
  // Spring
  // Exclude Commons Logging in favor of SLF4j
  "org.springframework" % "spring-context" % "3.0.3.RELEASE" exclude ("commons-logging", "commons-logging"),
  "org.springframework" % "spring-webmvc" % "3.0.3.RELEASE",
  "javax.inject" % "javax.inject" % "1",
  // Logging
  "org.slf4j" % "jcl-over-slf4j" % "1.5.10" % "runtime",
  "org.slf4j" % "slf4j-log4j12" % "1.5.10" % "runtime" force(),
  "org.slf4j" % "slf4j-api" % "1.5.10" force(),
  "log4j" % "log4j" % "1.2.16",
  "avalon-framework" % "avalon-framework" % "4.1.3",
  // JSR 303 with Hibernate Validator
  "javax.validation" % "validation-api" % "1.0.0.GA",
  "org.hibernate" % "hibernate-validator" % "4.0.2.GA",
  // URL Rewrite
  "org.tuckey" % "urlrewritefilter" % "3.1.0",
  // Servlet
  "javax.servlet" % "servlet-api" % "2.5" % "provided",
  "javax.servlet.jsp" % "jsp-api" % "2.1" % "provided",
  "javax.servlet" % "jstl" % "1.2",
  // for UriBuilder utility class
  "javax.ws.rs" % "jsr311-api" % "1.1.1",
  "com.sun.jersey" % "jersey-client" % "1.4",
  // Apache commons
  "commons-lang" % "commons-lang" % "2.5",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  // JSON parser
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.6.0",
  "org.codehaus.jackson" % 	"jackson-core-asl" % "1.6.0",
  // Test
  "junit" % "junit" % "4.4" % "test",
  "org.powermock.modules" % "powermock-module-junit4" % "1.4.5" % "test",
  "org.powermock.api" %	"powermock-api-easymock" % "1.4.5" % "test",
  "org.easymock" % "easymock" % "3.0" % "test",
  "org.springframework" % "spring-mock" % "2.0.8" % "test",
  "org.springframework" % "spring-test" % "2.5.6" % "test",
  "org.apache.tomcat" % "catalina" % "6.0.18" % "test",
  "org.apache.xbean" % "xbean-spring" % "3.7",
  "com.amazonaws" % "aws-java-sdk" % "1.2.9",
  // metrics
  "com.yammer.metrics" % "metrics-core" % "2.1.2",
  "com.yammer.metrics" % "metrics-servlet" % "2.1.2",
  "com.yammer.metrics" % "metrics-log4j" % "2.1.2",
  "com.yammer.metrics" % "metrics-graphite" % "2.1.2",
  "org.scala-lang" % "scala-library" % "2.10.0",
  "com.janrain" % "federate-utils" % "1.2.0",
  // intellij annotations library for @NotNull and @Nullable
  "org.kohsuke.jetbrains" % "annotations" % "9.0",
  "net.sf.ehcache" % "ehcache" % "2.5.2" pomOnly(),
  // Redis
  "redis.clients" % "jedis" % "2.1.0.a",
  "com.netflix.curator" % "curator-recipes" % "1.1.15",
  // supersimpledb
  "com.janrain.commons.supersimpledb" % "commons-supersimpledb" % "1.0.27",
  // Analytics (flume, akka, scalax, etc.)
  "com.github.scala-incubator.io" % "scala-io-file_2.10" % "0.4.2",
  "com.typesafe.akka" % "akka-actor_2.10" % "2.1.1",
  "log4j" % "log4j" % "1.2.16",
  "com.cloudera" % "flume-log4j-appender" % "0.9.4-cdh3u3" intransitive(),
  "com.cloudera" % "flume-core" % "0.9.4-cdh3u3" intransitive(),
  "org.apache.avro" % "avro-ipc" % "1.5.4" intransitive()
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
  "terracotta-releases" at "http://www.terracotta.org/download/reflector/releases",
  "cloudera" at "https://repository.cloudera.com/artifactory/cloudera-repos/"
)
