package mailers.User

import play.api.i18n.Messages
import play.api.mvc.RequestHeader

import mailers.Mailer
import models.OverviewUser

case class createErrorUserAlreadyExists(val user: OverviewUser)(implicit val messages: Messages, val request: RequestHeader) extends Mailer {
  private val m = views.Magic.scopedMessages("mailers.User.createErrorUserAlreadyExists")

  private val url = controllers.routes.SessionController._new().absoluteURL()
  private val intro = m("intro")
  private val case1 = m("case1")
  private val case2 = m("case2")
  private val signoff = m("signoff")
  private val signature = m("signature")

  override val recipients = Seq(user.email)
  override val subject = m("subject")
  override val text = intro + "\n\n" + case1 + "\n\n" + case2 + "\n\n" + url + "\n\n" + signoff + "\n" + signature
  override val html =
    <html lang={messages.lang.code}>
      <head>
        <title>{subject}</title>
      </head>
      <body>
        <p>{intro}</p>
        <p>{case1}</p>
        <p>{case2}</p>
        <p><a href={url}>{url}</a></p>
        <p>{signoff}<br/>{signature}</p>
      </body>
    </html>
}
