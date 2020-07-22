package io.github.pauljamescleary.petstore.domain.pets

import cats.data.NonEmptyList
import derevo.derive
import tofu.data.derived.ContextEmbed
import tofu.higherKind.derived.representableK

@derive(representableK)
trait PetRepositoryAlgebra[F[_]] {
  def create(pet: Pet): F[Pet]
  def update(pet: Pet): F[Option[Pet]]

  def get(id: Long): F[Option[Pet]]

  def delete(id: Long): F[Option[Pet]]

  def findByNameAndCategory(name: String, category: String): F[Set[Pet]]

  def list(pageSize: Int, offset: Int): F[List[Pet]]

  def findByStatus(status: NonEmptyList[PetStatus]): F[List[Pet]]

  def findByTag(tags: NonEmptyList[String]): F[List[Pet]]
}

object PetRepositoryAlgebra extends ContextEmbed[PetRepositoryAlgebra]{

}
