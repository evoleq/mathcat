package org.evoleq.mathcat.core.collections

import org.evoleq.mathcat.core.optics.Selection
import org.evoleq.mathcat.core.reader.Reader
import org.evoleq.mathcat.core.writer.Writer

/** 
 * Filter/replace selection, deliberately NOT a lawful Lens:
 * replacing the selected values with themselves may reorder a list.
 */
@Suppress("FunctionName")
fun <A> FilterBy(predicate: (A) -> Boolean): Selection<List<A>, List<A>> =
    Selection(
        get = Reader { source -> source.filter(predicate) },
        set = Writer { replacement ->
            { source -> source.filterNot(predicate) + replacement }
        }
    )
