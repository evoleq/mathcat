package evoleq.toolkit.core.collections

import evoleq.toolkit.core.optics.Selection
import evoleq.toolkit.core.reader.Reader
import evoleq.toolkit.core.writer.Writer

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
