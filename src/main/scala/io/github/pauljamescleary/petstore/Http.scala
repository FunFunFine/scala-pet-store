package io.github.pauljamescleary.petstore

import cats.effect.{ConcurrentEffect, Resource, Sync, Timer}
import doobie.util.ExecutionContexts
import io.github.pauljamescleary.petstore.config.PetStoreConfig
import io.github.pauljamescleary.petstore.domain.authentication.Authenticate
import io.github.pauljamescleary.petstore.domain.orders.OrderService
import io.github.pauljamescleary.petstore.domain.pets.PetService
import io.github.pauljamescleary.petstore.domain.users.UserService
import io.github.pauljamescleary.petstore.infrastructure.endpoint.{
  OrderEndpoints,
  PetEndpoints,
  UserEndpoints,
}
import org.http4s.HttpApp
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import tsec.mac.jca.HMACSHA256
import tsec.passwordhashers.jca.BCrypt

trait Http[F[_]] {
  def app: HttpApp[F]
}

object Http {

  def mkServer[F[_]: ConcurrentEffect: Timer](
      httpApp: HttpApp[F],
      config: PetStoreConfig,
  ): Resource[F, Server[F]] =
    for {
      serverEc <- ExecutionContexts.cachedThreadPool[F]

      server <- BlazeServerBuilder[F](serverEc)
        .bindHttp(config.server.port, config.server.host)
        .withHttpApp(httpApp)
        .resource

    } yield server

  def mkApp[F[_]: Sync](
      implicit
      routeAuth: Authenticate[F, HMACSHA256],
      petService: PetService[F],
      userService: UserService[F],
      orderService: OrderService[F],
  ): Http[F] = new Http[F] {
    override def app: HttpApp[F] =
      Router(
        "/users" -> UserEndpoints
          .endpoints[F, BCrypt, HMACSHA256](
            userService,
            BCrypt.syncPasswordHasher[F],
            routeAuth,
          ),
        "/pets" -> PetEndpoints.endpoints[F, HMACSHA256](petService, routeAuth),
        "/orders" -> OrderEndpoints.endpoints[F, HMACSHA256](orderService, routeAuth),
      ).orNotFound

  }

}
