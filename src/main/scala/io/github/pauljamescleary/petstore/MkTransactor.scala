package io.github.pauljamescleary.petstore

import cats.effect.{Async, Blocker, ContextShift, Resource}
import cats.{Applicative, Defer}
import doobie.ExecutionContexts
import doobie.util.transactor.Transactor
import io.github.pauljamescleary.petstore.config.DatabaseConfig
import tofu.lift.Lift

object MkTransactor {

  def make[I[_]: Async: ContextShift, F[_]: Defer: Applicative: Lift[I, *[_]]](
      config: DatabaseConfig,
  ): Resource[I, Transactor[F]] =
    for {
      connEc <- ExecutionContexts.fixedThreadPool[I](config.connections.poolSize)
      txnEc <- ExecutionContexts
        .cachedThreadPool[I]
      xa <- DatabaseConfig
        .dbTransactor[I](config, connEc, Blocker.liftExecutionContext(txnEc))
    } yield xa.mapK(Lift[I, F].liftF)

}
