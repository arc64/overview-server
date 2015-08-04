package com.overviewdocs.jobhandler.documentset

import play.api.libs.json._
import com.overviewdocs.jobhandler.documentset.DocumentSetJobHandlerProtocol._
import com.overviewdocs.messagequeue.ConvertMessage


/** Converts messages from the queue into specific Command Messages */
object ConvertDocumentSetMessage extends ConvertMessage {
  private val DeleteCmdMsg = "delete"
  private val DeleteTreeJobCmdMsg = "delete_tree_job"

  private val deleteCommandReads = Json.reads[DeleteCommand]
  private val deleteTreeJobCommandReads = Json.reads[DeleteTreeJobCommand]
  
  def apply(message: String): Command = {
    val m = getMessage(message)

    m.cmd match {
      case DeleteCmdMsg => deleteCommandReads.reads(m.args).get
      case DeleteTreeJobCmdMsg => deleteTreeJobCommandReads.reads(m.args).get
    }

  }
}
