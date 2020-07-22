package io.github.pauljamescleary.petstore

import cats.effect.{Async, Blocker, ContextShift, Resource}
import doobie.ExecutionContexts
import doobie.util.transactor.Transactor
import io.github.pauljamescleary.petstore.config.DatabaseConfig

object MkTransactor {

  def makeF[F[_]: Async: ContextShift](
      config: DatabaseConfig,
  ): Resource[F, Transactor[F]] =
    for {
      connEc <- ExecutionContexts.fixedThreadPool[F](config.connections.poolSize)
      txnEc <- ExecutionContexts
        .cachedThreadPool[F]
      xa <- DatabaseConfig
        .dbTransactor[F](config, connEc, Blocker.liftExecutionContext(txnEc))
    } yield xa

}
