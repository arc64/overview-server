package com.overviewdocs.runner.commands

trait WithAppendableClasspath { self: JvmCommand =>
  def withClasspath(paths: Seq[String]) : JvmCommand with WithAppendableClasspath = {
    val classPathArg = paths.mkString(":")
    val newJvmArgs = self.jvmArgs ++ Seq("-cp", classPathArg)
    new JvmCommandWithAppendableClasspath(self.env, newJvmArgs, self.appArgs)
  }
}

class JvmCommandWithAppendableClasspath(env: Seq[(String,String)], jvmArgs: Seq[String], args: Seq[String])
  extends JvmCommand(env, jvmArgs, args) with WithAppendableClasspath
