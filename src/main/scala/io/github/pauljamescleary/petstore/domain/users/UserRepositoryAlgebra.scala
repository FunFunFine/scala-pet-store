package io.github.pauljamescleary.petstore.domain.users

import cats.data.OptionT
import derevo.derive
import tofu.data.derived.ContextEmbed
import tofu.higherKind.derived.representableK
import tsec.authentication.IdentityStore

@derive(representableK)
trait UserRepositoryAlgebra[F[_]] extends IdentityStore[F, Long, User]{
  def create(user: User): F[User]

  def update(user: User): OptionT[F, User]

  def get(userId: Long): OptionT[F, User]

  def delete(userId: Long): OptionT[F, User]

  def findByUserName(userName: String): OptionT[F, User]

  def deleteByUserName(userName: String): OptionT[F, User]

  def list(pageSize: Int, offset: Int): F[List[User]]
}

object UserRepositoryAlgebra extends ContextEmbed[UserRepositoryAlgebra] {

}
