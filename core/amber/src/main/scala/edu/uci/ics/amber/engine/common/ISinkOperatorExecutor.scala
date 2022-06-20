package edu.uci.ics.amber.engine.common

import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.LinkIdentity

trait ISinkOperatorExecutor extends IOperatorExecutor {

  override def processTuple(
      tuple: Either[ITuple, InputExhausted],
      input: LinkIdentity
  ): Iterator[(ITuple, Option[LinkIdentity])] = {
    consume(tuple, input)
    Iterator.empty
  }

  def consume(tuple: Either[ITuple, InputExhausted], input: LinkIdentity): Unit
}
