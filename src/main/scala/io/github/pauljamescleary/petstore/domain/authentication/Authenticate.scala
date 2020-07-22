package io.github.pauljamescleary.petstore.domain.authentication

import cats.effect._
import doobie.util.transactor.Transactor
import io.github.pauljamescleary.petstore.domain.users.{User, UserRepositoryAlgebra}
import io.github.pauljamescleary.petstore.infrastructure.endpoint.AuthService
import io.github.pauljamescleary.petstore.infrastructure.repository.doobie.DoobieAuthRepositoryInterpreter
import org.http4s.HttpRoutes
import tofu.syntax.monadic._
import tsec.authentication.{AugmentedJWT, Authenticator, SecuredRequestHandler}
import tsec.mac.jca.HMACSHA256

trait Authenticate[F[_], Auth] {
  def secureService(service: AuthService[F, Auth]): HttpRoutes[F]
  def authenticator: Authenticator[F, Long, User, AugmentedJWT[Auth, Long]]
}

object Authenticate {
  def makeHMACSHA256[F[_]: Sync](
      xa: Transactor[F],
      userRepository: UserRepositoryAlgebra[F],
  ): F[Authenticate[F, HMACSHA256]] =
    for {
      key <- HMACSHA256.generateKey[F]
      authRepo = DoobieAuthRepositoryInterpreter[F, HMACSHA256](key, xa)
      authenticator = Auth.jwtAuthenticator[F, HMACSHA256](key, authRepo, userRepository)
      handler = SecuredRequestHandler.apply(authenticator)
    } yield new Authenticate[F, HMACSHA256] {
      override def secureService(service: AuthService[F, HMACSHA256]): HttpRoutes[F] =
        handler.liftService(service)

      override def authenticator: Authenticator[F, Long, User, AugmentedJWT[HMACSHA256, Long]] =
        handler.authenticator
    }
}
