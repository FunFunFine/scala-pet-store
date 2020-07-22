package io.github.pauljamescleary.petstore.domain
package users

import cats.data._
import cats.Monad
import tofu.data.derived.ContextEmbed
import tofu.syntax.monadic._

final class UserService[F[_]: Monad](
    userRepo: UserRepositoryAlgebra[F],
    validation: UserValidation[F],
) {
  def createUser(user: User): EitherT[F, UserAlreadyExistsError, User] =
    for {
      _ <- validation.doesNotExist(user)
      saved <- EitherT.liftF(userRepo.create(user))
    } yield saved

  def getUser(userId: Long): EitherT[F, UserNotFoundError.type, User] =
    userRepo.get(userId).toRight(UserNotFoundError)

  def getUserByName(
      userName: String,
  ): EitherT[F, UserNotFoundError.type, User] =
    userRepo.findByUserName(userName).toRight(UserNotFoundError)

  def deleteUser(userId: Long): F[Unit] =
    userRepo.delete(userId).value.void

  def deleteByUserName(userName: String): F[Unit] =
    userRepo.deleteByUserName(userName).value.void

  def update(user: User): EitherT[F, UserNotFoundError.type, User] =
    for {
      _ <- validation.exists(user.id)
      saved <- userRepo.update(user).toRight(UserNotFoundError)
    } yield saved

  def list(pageSize: Int, offset: Int): F[List[User]] =
    userRepo.list(pageSize, offset)
}

object UserService extends ContextEmbed[UserService] {
  def make[F[_]: Monad](
      implicit userRepo: UserRepositoryAlgebra[F],
      validation: UserValidation[F],
  ) = new UserService[F](userRepo, validation)
}
