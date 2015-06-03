package models

import java.sql.Timestamp

import org.overviewproject.models.UserRole

case class User(
  id: Long = 0L,
  email: String = "user@example.org",
  passwordHash: String = "",
  role: UserRole.Value = UserRole.NormalUser,
  confirmationToken: Option[String] = None,
  confirmationSentAt: Option[Timestamp] = None,
  confirmedAt: Option[Timestamp] = None,
  resetPasswordToken: Option[String] = None,
  resetPasswordSentAt: Option[Timestamp] = None,
  lastActivityAt: Option[Timestamp] = None,
  lastActivityIp: Option[String] = None,
  emailSubscriber: Boolean = false,
  treeTooltipsEnabled: Boolean = true
) {
  def this() = this(role = UserRole.NormalUser) // For Squeryl

  def isAdministrator = role == UserRole.Administrator
}

object User {
  case class CreateAttributes(
    email: String,
    passwordHash: String,
    role: UserRole.Value = UserRole.NormalUser,
    confirmationToken: Option[String] = None,
    confirmationSentAt: Option[Timestamp] = None,
    confirmedAt: Option[Timestamp] = None,
    resetPasswordToken: Option[String] = None,
    resetPasswordSentAt: Option[Timestamp] = None,
    lastActivityAt: Option[Timestamp] = None,
    lastActivityIp: Option[String] = None,
    emailSubscriber: Boolean = false,
    treeTooltipsEnabled: Boolean = true
  )
}
