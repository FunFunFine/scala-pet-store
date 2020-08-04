package io.github.pauljamescleary.petstore.config

import tofu.optics.macros.ClassyOptics

final case class ServerConfig(host: String, port: Int)
@ClassyOptics
final case class PetStoreConfig(db: DatabaseConfig, server: ServerConfig)
