package dev.scrollwerk.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesTest {
    private val text = "# Shopping\n- [ ] milk\n- [x] bread\n\nCall the plumber\n  * [X] nested\n"

    @Test fun parsesTasksHeadingsAndText() {
        assertEquals(
            listOf(
                NoteLine.Heading(0, "Shopping"),
                NoteLine.Task(1, false, "milk"),
                NoteLine.Task(2, true, "bread"),
                NoteLine.Blank(3),
                NoteLine.Text(4, "Call the plumber"),
                NoteLine.Task(5, true, "nested"),
            ),
            Notes.parse(text),
        )
    }

    @Test fun emptyFileHasOneBlankLine() {
        assertEquals(listOf(NoteLine.Blank(0)), Notes.parse(""))
    }

    @Test fun togglingChangesOnlyThatLine() {
        assertEquals(text.replace("- [ ] milk", "- [x] milk"), Notes.toggle(text, 1))
        assertEquals(text.replace("- [x] bread", "- [ ] bread"), Notes.toggle(text, 2))
        assertEquals(text.replace("  * [X] nested", "  * [ ] nested"), Notes.toggle(text, 5))
    }

    @Test fun togglingSomethingElseLeavesTheTextAlone() {
        assertEquals(text, Notes.toggle(text, 0)) // heading
        assertEquals(text, Notes.toggle(text, 99)) // no such line
    }

    @Test fun keepsWindowsLineEndings() {
        assertEquals("- [x] a\r\n- [ ] b", Notes.toggle("- [ ] a\r\n- [ ] b", 0))
    }

    @Test fun notALinkOrBracketText() {
        assertEquals(listOf(NoteLine.Text(0, "[ ] not a task"), NoteLine.Bullet(1, "[link](x)")), Notes.parse("[ ] not a task\n- [link](x)"))
    }

    // ---- formatting buttons

    private fun fmt(text: String, start: Int, end: Int, kind: Notes.Kind) = Notes.format(Notes.Edit(text, start, end), kind)

    @Test fun checkboxButtonMakesTheCursorLineATask() {
        // "milk" with the cursor after "mi"
        assertEquals(Notes.Edit("- [ ] milk", 8, 8), fmt("milk", 2, 2, Notes.Kind.Task))
    }

    @Test fun pressingTheSameButtonAgainUndoesIt() {
        assertEquals(Notes.Edit("milk", 2, 2), fmt("- [ ] milk", 8, 8, Notes.Kind.Task))
    }

    @Test fun anotherButtonReplacesTheMarker() {
        assertEquals(Notes.Edit("- [ ] milk", 8, 8), fmt("- milk", 4, 4, Notes.Kind.Task))
        assertEquals(Notes.Edit("# milk", 4, 4), fmt("- [x] milk", 8, 8, Notes.Kind.Heading))
    }

    @Test fun formatsEveryLineInTheSelectionAndLeavesOthers() {
        val text = "intro\nmilk\nbread\nend"
        // select from inside "milk" to inside "bread"
        val r = fmt(text, 8, 14, Notes.Kind.Bullet)
        assertEquals("intro\n- milk\n- bread\nend", r.text)
        assertEquals(10, r.start) // still after "mi"
        assertEquals(18, r.end) // still after "bre"
    }

    @Test fun mixedSelectionAllBecomeTheKind() {
        assertEquals("- [ ] a\n- [ ] b", fmt("- [ ] a\nb", 0, 9, Notes.Kind.Task).text)
    }

    @Test fun keepsIndentation() {
        assertEquals("  - [ ] sub", fmt("  - sub", 7, 7, Notes.Kind.Task).text)
    }

    @Test fun cursorInsideTheMarkerLandsAfterIt() {
        assertEquals(Notes.Edit("# milk", 2, 2), fmt("- [ ] milk", 3, 3, Notes.Kind.Heading))
    }

    @Test fun emptyNoteGetsAMarker() {
        assertEquals(Notes.Edit("- [ ] ", 6, 6), fmt("", 0, 0, Notes.Kind.Task))
    }

    // ---- Enter in a list

    private fun enter(old: String, at: Int) =
        Notes.continueList(old, Notes.Edit(old.substring(0, at) + "\n" + old.substring(at), at + 1))

    @Test fun enterAfterATaskStartsAnUntickedOne() {
        assertEquals(Notes.Edit("- [x] milk\n- [ ] ", 17), enter("- [x] milk", 10))
    }

    @Test fun enterAfterABulletStartsABullet() {
        assertEquals(Notes.Edit("  - a\n  - \nnext", 10), enter("  - a\nnext", 5))
    }

    @Test fun enterOnAnEmptyItemEndsTheList() {
        assertEquals(Notes.Edit("- [ ] milk\n", 11), enter("- [ ] milk\n- [ ] ", 17))
    }

    @Test fun enterElsewhereIsJustANewLine() {
        assertEquals(Notes.Edit("plain\n", 6), enter("plain", 5))
        assertEquals(Notes.Edit("# Title\n", 8), enter("# Title", 7))
    }

    @Test fun typingOtherThingsIsLeftAlone() {
        val e = Notes.Edit("- [ ] milkx", 11)
        assertEquals(e, Notes.continueList("- [ ] milk", e))
    }

    @Test fun cardShowsBullets() {
        assertEquals(listOf(NoteLine.Bullet(0, "eggs")), Notes.parse("- eggs"))
    }
}

class NotesMarkerTest {
    @Test fun findsMarkersWithTheirPositions() {
        assertEquals(
            listOf(
                Notes.Marker(0, 2, Notes.Kind.Heading),
                Notes.Marker(4, 6, Notes.Kind.Task),
                Notes.Marker(12, 6, Notes.Kind.Task, checked = true),
                Notes.Marker(24, 2, Notes.Kind.Bullet),
            ),
            Notes.markers("# A\n- [ ] b\n- [x] c\nd\n  - e"),
        )
    }

    @Test fun backspaceIntoACheckboxRemovesIt() {
        // cursor after "- [ ] ", backspace deletes the space
        assertEquals(Notes.Edit("milk", 0), Notes.backspaceMarker("- [ ] milk", Notes.Edit("- [ ]milk", 5)))
        assertEquals(Notes.Edit("a\nb", 2), Notes.backspaceMarker("a\n- b", Notes.Edit("a\n-b", 3)))
    }

    @Test fun ordinaryBackspaceIsLeftAlone() {
        val e = Notes.Edit("- [ ] mil", 9)
        assertEquals(e, Notes.backspaceMarker("- [ ] milk", e))
    }
}
