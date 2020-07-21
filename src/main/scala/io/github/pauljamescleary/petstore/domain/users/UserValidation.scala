package io.github.pauljamescleary.petstore.domain
package users

import cats.Monad
import cats.data.EitherT
import derevo.derive
import tofu.data.derived.ContextEmbed
import tofu.higherKind.derived.representableK
import tofu.syntax.monadic._

@derive(representableK)
trait UserValidation[F[_]] {
  def doesNotExist(user: User): EitherT[F, UserAlreadyExistsError, Unit]

  def exists(userId: Option[Long]): EitherT[F, UserNotFoundError.type, Unit]
}

object UserValidation extends ContextEmbed[UserValidation] {
  def make[F[_]: Monad](implicit userRepo: UserRepositoryAlgebra[F]): UserValidation[F] =
    new UserValidation[F] {
      def doesNotExist(user: User): EitherT[F, UserAlreadyExistsError, Unit] =
        userRepo
          .findByUserName(user.userName)
          .map(UserAlreadyExistsError)
          .toLeft(())

      def exists(userId: Option[Long]): EitherT[F, UserNotFoundError.type, Unit] =
        userId match {
          case Some(id) =>
            userRepo
              .get(id)
              .toRight(UserNotFoundError)
              .void
          case None =>
            EitherT.left[Unit](UserNotFoundError.pure[F])
        }
    }

}
