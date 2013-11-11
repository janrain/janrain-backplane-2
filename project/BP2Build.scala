import sbt._
import sbt.Keys._
import sbtbuildinfo.Plugin._

object BP2Build extends Build {

  val bp2Project = Project(
    id = "BP2",
    base = file("."),
    settings =
      Defaults.defaultSettings ++
        buildInfoSettings ++
        Seq(
          sourceGenerators in Compile <+= buildInfo,
          buildInfoPackage := "com.janrain.backplane",
          buildInfoKeys := Seq[BuildInfoKey](BuildInfoKey("name", "BP2"), scalaVersion, sbtVersion)
        )
  )
    .settings(
    buildInfoKeys ++= Seq[BuildInfoKey](
      BuildInfoKey.map(version) { case (k,v) => k -> buildVersion }
    )
  )

  lazy val repo = (new org.eclipse.jgit.storage.file.FileRepositoryBuilder).findGitDir.build
  lazy val gitBranch = sys.env.get("GIT_BRANCH").getOrElse(repo.getBranch).stripPrefix("origin/").replace('/', '_')
  lazy val lastCommitHash = new org.eclipse.jgit.api.Git(repo).log().call().iterator.next.getId.name.substring(0,8)
  lazy val buildVersion = gitBranch + "-" + lastCommitHash + "-" + buildId
  lazy val buildId = sys.env.get("BUILD_NUMBER") getOrElse
                     (System.getProperty("user.name") + "@" +java.net.InetAddress.getLocalHost.getHostName)

}
