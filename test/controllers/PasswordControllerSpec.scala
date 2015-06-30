package controllers

import java.util.Date
import org.specs2.specification.Scope
import play.api.mvc.RequestHeader
import scala.concurrent.Future

import controllers.backend.SessionBackend
import models.{OverviewUser, ResetPasswordRequest, Session, User}

class PasswordControllerSpec extends ControllerSpecification {
  trait OurScope extends Scope {
    trait UserWithRequest extends OverviewUser with ResetPasswordRequest

    // We have a mock "user" and "userWithRequest" and they return each other
    val user = smartMock[OverviewUser]
    user.email returns "user@example.org"
    user.passwordMatches("hash") returns true
    user.toUser returns User(id=123L, email="user@example.org")

    val userWithRequest = smartMock[UserWithRequest]
    userWithRequest.email returns user.email
    userWithRequest.resetPasswordToken returns "0123456789abcd"
    userWithRequest.withNewPassword(anyString) returns user

    user.withResetPasswordRequest returns userWithRequest

    val mockStorage = smartMock[PasswordController.Storage]
    val mockMail = smartMock[PasswordController.Mail]
    val mockSessionBackend = smartMock[SessionBackend]

    mockStorage.findUserByEmail(any[String]) returns None
    mockStorage.findUserByEmail("user@example.org") returns Some(user)
    mockStorage.findUserByResetToken(any[String]) returns None
    mockStorage.findUserByResetToken("0123456789abcd") returns Some(userWithRequest)
    mockSessionBackend.create(any[Long], any[String]) returns Future.successful(Session(123L, "127.0.0.1"))
    mockStorage.insertOrUpdateUser(any[User]) answers { x => x.asInstanceOf[User] }

    val controller = new PasswordController {
      override val sessionBackend = mockSessionBackend
      override val storage = mockStorage
      override val mail = mockMail
    }
  }

  "PasswordController" should {
    "_new()" should {
      trait NewScope extends OurScope {
        def request = fakeOptionallyAuthorizedRequest(None)
        lazy val result = controller._new()(request)
      }

      "redirect when the user is logged in" in new NewScope {
        override def request = fakeOptionallyAuthorizedRequest(Some(fakeUser))
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "show some HTML when the user is not logged in" in new NewScope {
        h.status(result) must beEqualTo(h.OK)
      }
    }

    "edit()" should {
      trait EditScope extends OurScope {
        def request = fakeOptionallyAuthorizedRequest(None)
        val token: String = "0123456789abcd"
        lazy val result = controller.edit(token)(request)
      }

      "redirect when the user is logged in" in new EditScope {
        override def request = fakeOptionallyAuthorizedRequest(Some(fakeUser))
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "show the edit page" in new EditScope {
        h.status(result) must beEqualTo(h.OK)
        h.contentAsString(result) must contain("<form")
      }

      "show an 'invalid token page'" in new EditScope {
        override val token = "bad-token"
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }
    }

    "create()" should {
      trait CreateScope extends OurScope {
        val params : Seq[(String,String)] = Seq()
        def request = fakeOptionallyAuthorizedRequest(None).withFormUrlEncodedBody(params: _*)
        lazy val result = controller.create()(request)
      }

      "return BadRequest and show a form when the user enters an invalid email address" in new CreateScope {
        override val params = Seq("email" -> ".")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must contain("<form")
      }

      "when the user is not found" should {
        trait CreateScopeUserNotFound extends CreateScope {
          override val params = Seq("email" -> "invalid@example.org")
          h.status(result) // run
        }

        "email the non-user" in new CreateScopeUserNotFound {
          there was one(mockMail).sendCreateErrorUserDoesNotExist(any[String])(any[RequestHeader])
        }

        "redirect" in new CreateScopeUserNotFound {
          h.status(result) must beEqualTo(h.SEE_OTHER)
        }

        "flash a message" in new CreateScopeUserNotFound {
          h.flash(result).get("success") must beSome("We have sent an email to invalid@example.org with instructions.")
        }

        "not change the database" in new CreateScopeUserNotFound {
          there was no(mockStorage).insertOrUpdateUser(any[User])
        }
      }

      "when the user is found" should {
        trait CreateScopeUserFound extends CreateScope {
          override val params = Seq("email" -> "user@example.org")
          h.status(result) // run
        }

        "email the user" in new CreateScopeUserFound {
          there was one(mockMail).sendCreated(any[OverviewUser with ResetPasswordRequest])(any[RequestHeader])
        }

        "redirect" in new CreateScopeUserFound {
          h.status(result) must beEqualTo(h.SEE_OTHER)
        }

        "flash a message" in new CreateScopeUserFound {
          h.flash(result).get("success") must beSome("We have sent an email to user@example.org with instructions.")
        }

        "change the database" in new CreateScopeUserFound {
          there was one(user).withResetPasswordRequest
          there was one(mockStorage).insertOrUpdateUser(any[User])
        }
      }
    }

    "update()" should {
      trait UpdateScope extends OurScope {
        val params : Seq[(String,String)] = Seq("password" -> "Ersh3Phowb9")
        val token : String = "0123456789abcd"
        def request = fakeOptionallyAuthorizedRequest(None).withFormUrlEncodedBody(params: _*)
        lazy val result = controller.update(token)(request)
      }

      "show an invalid-token page" in new UpdateScope {
        override val token = "bad-token"
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.session(result).get("AUTH_USER_ID") must beNone
      }

      "show an error and form when given a bad password" in new UpdateScope {
        override val params = Seq("password" -> "")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        h.contentAsString(result) must contain("<form")
        h.session(result).get("AUTH_USER_ID") must beNone
      }

      "show the actual error message when given a bad password" in new UpdateScope {
        // Issue #254
        override val params = Seq("password" -> "a")
        h.contentAsString(result) must contain("""<fieldset class="control-group error""")
      }

      "save the user with a new password" in new UpdateScope {
        h.status(result)
        there was one(userWithRequest).withNewPassword("Ersh3Phowb9")
        there was one(mockStorage).insertOrUpdateUser(any[User])
      }

      "log the user in" in new UpdateScope {
        h.session(result).get("AUTH_SESSION_ID") must beSome
        there was one(mockSessionBackend).create(any[Long], any[String])
      }

      "redirect" in new UpdateScope {
        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "flash that the password was changed" in new UpdateScope {
        h.flash(result).get("success") must beSome("You have updated your password, and you are now logged in.")
      }
    }
  }
}
