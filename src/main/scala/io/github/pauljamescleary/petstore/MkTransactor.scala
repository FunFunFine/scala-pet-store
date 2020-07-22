package io.github.pauljamescleary.petstore

import cats.Monad
import cats.effect.{Async, ContextShift}
import doobie.util.transactor.Transactor
import io.github.pauljamescleary.petstore.config.DatabaseConfig

object MkTransactor {
  def apply[I[_]: Monad, F[_]: Async: ContextShift](
      config: DatabaseConfig,
  ): I[Transactor[F]] = ???
//    for {
//      connEc <- ExecutionContexts.fixedThreadPool[F](config.connections.poolSize).lift[I]
//      txnEc <- ExecutionContexts
//        .cachedThreadPool[F]
//        .lift[I]
//      xa <- DatabaseConfig
//        .dbTransactor[F](config, connEc, Blocker.liftExecutionContext(txnEc))
//        .lift[I]
//    } yield xa
}
