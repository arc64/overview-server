package org.overviewproject.runner

import java.io.File
import scala.io.Source

package object commands {
  trait UsefulCommands {
    def documentSetWorker: Command
    def messageBroker: Command
    def searchIndex: Command
    def redis: Command
    def webServer: Command
    def worker: Command
    def runEvolutions: Command

    /** Runs sbt. If not present, throws a run-time exception. */
    def sbt(task: String): Command = ???

    def sh(task: String): Command = new ShCommand(Seq(), task.split(' '))
  }

  object development extends UsefulCommands {
    private lazy val sbtLaunchJar = getClass.getResource("/sbt-launch.jar")
    private lazy val sbtLaunchPath = new File(sbtLaunchJar.toURI()).getAbsolutePath()

    override def searchIndex = new JvmCommandWithAppendableClasspath(
      Seq(),
      Seq(
        "-Xmx1g",
        "-Xss256k",
        "-XX:+UseParNewGC",
        "-XX:+UseConcMarkSweepGC",
        "-XX:CMSInitiatingOccupancyFraction=75",
        "-XX:+UseCMSInitiatingOccupancyOnly",
        "-Djava.awt.headless=true",
        "-Delasticsearch",
        "-Des.foreground=yes",
        "-Des.path.home=./search-index"
      ),
      Seq("org.elasticsearch.bootstrap.Bootstrap")
    )

    override def messageBroker = new JvmCommandWithAppendableClasspath(
      Seq(),
      Seq(Flags.ApolloBase),
      Seq(
        "org.apache.activemq.apollo.boot.Apollo",
        "documentset-worker/lib",
        "org.apache.activemq.apollo.cli.Apollo",
        "run"
      )
    )

    override def webServer = new JvmCommand(
      // We run "overview-server/run" through sbt. That lets it reload files
      // as they're edited.
      Seq(),
      Seq(
        "-Duser.timezone=UTC",
        "-Dpidfile.enabled=false",
        "-Dsbt.jse.engineType=" + scala.sys.props.getOrElse("sbt.jse.engineType", "Trireme")
      ),
      Seq(
        "-jar", sbtLaunchPath,
        "run"
      )
    )

    override def documentSetWorker = new JvmCommandWithAppendableClasspath(
      Flags.DatabaseEnv,
      Seq(),
      Seq("org.overviewproject.DocumentSetWorker")
    )

    override def redis = new ShCommand(Seq(), Seq("./deps/redis/dev.sh"))

    override def worker = new JvmCommandWithAppendableClasspath(
      Flags.DatabaseEnv,
      Seq("-Xmx2g"),
      Seq("JobHandler")
    ).with32BitSafe

    override def runEvolutions = new JvmCommand(
      Flags.DatabaseEnv,
      Seq("-Dsbt.log.format=false"),
      Seq(
        "-jar", sbtLaunchPath,
        "db-evolution-applier/run"
      )
    )

    /** A Command for "sbt [task]" */
    override def sbt(task: String) = {
      val currentSbtArgs = sys.props
        .toIterable
        .map { case (k: String, v: String) =>
          if (k.startsWith("sbt.")) {
            Seq("-D" + k + "=" + v)
          } else {
            Seq()
          }
        }
        .flatten.toSeq

      new JvmCommand(
        Seq(),
        currentSbtArgs ++ Seq(
          "-Dsbt.log.format=false",
          "-Xmx2g"
        ),
        Seq(
          "-jar", sbtLaunchPath,
          task
        )
      ).with32BitSafe
    }
  }

  object production extends UsefulCommands {
    private def cmd(prefix: String, env: Seq[(String,String)], jvmArgs: Seq[String], args: Seq[String]) = {
      val classPath = Source
        .fromFile(s"${prefix}/classpath.txt")
        .getLines
        .map((s) => s"lib/${s}")
        .mkString(File.pathSeparator)

      val fullJvmArgs = jvmArgs ++ Seq("-cp", classPath)
      new JvmCommand(env, fullJvmArgs, args)
    }

    override def documentSetWorker = cmd(
      "documentset-worker",
      Flags.DatabaseEnv,
      Seq(),
      Seq("org.overviewproject.DocumentSetWorker")
    )

    override def messageBroker = cmd(
      "message-broker",
      Seq(),
      Seq(Flags.ApolloBase),
      Seq(
        "org.apache.activemq.apollo.boot.Apollo",
        "documentset-worker/lib",
        "org.apache.activemq.apollo.cli.Apollo",
        "run"
      )
    )

    override def searchIndex = cmd(
      "search-index",
      Seq(),
      Seq(
        "-Xmx1g",
        "-Xss256k",
        "-XX:+UseParNewGC",
        "-XX:+UseConcMarkSweepGC",
        "-XX:CMSInitiatingOccupancyFraction=75",
        "-XX:+UseCMSInitiatingOccupancyOnly",
        "-Djava.awt.headless=true",
        "-Delasticsearch",
        "-Des.foreground=yes",
        "-Des.path.home=./search-index",
        // Put the search index in the same directory as Postgres. That way
        // all user data gets stored in one directory. (Yes, it's a bit of a
        // hack.)
        "-Des.path.data=database/search-index"
      ),
      Seq("org.elasticsearch.bootstrap.Bootstrap")
    )

    override def redis = new ShCommand(Seq(), Seq("./deps/redis/dev.sh"))

    override def webServer = cmd(
      // In distribution mode, the web server is in the "frontend/" folder
      "frontend",
      Flags.DatabaseEnv,
      Seq(
        "-Duser.timezone=UTC",
        "-Dpidfile.enabled=false"
      ),
      Seq("play.core.server.NettyServer")
    )

    override def worker = cmd(
      "worker",
      Flags.DatabaseEnv,
      Seq("-Xmx2g"),
      Seq("JobHandler")
    ).with32BitSafe

    override def runEvolutions = cmd(
      "db-evolution-applier",
      Flags.DatabaseEnv,
      Seq(),
      Seq("org.overviewproject.db_evolution_applier.Main")
    )
  }
}
