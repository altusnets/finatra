package org.apache.kafka.streams.state.internals

/**
 * Factory to allow us to call the package private RocksDBStore constructor
 */
object RocksDBStoreFactory {
  def create(name: String): RocksDBStore = {
    new RocksDBStore(name)
  }
}
