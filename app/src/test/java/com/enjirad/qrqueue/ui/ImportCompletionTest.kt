package com.enjirad.qrqueue.ui

import com.enjirad.qrqueue.domain.ImportProgress
import com.enjirad.qrqueue.domain.ImportSummary
import com.enjirad.qrqueue.domain.QueueImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The import screen must end by itself: once every selected image has been
 * handled (copied or failed) and the queue is saved, the app shows the queue
 * again — with no Continue, Done or Next tap, and never stuck on "5 / 5".
 */
class ImportCompletionTest {

    @Test
    fun oneSelectedImageFinishesTheImport() {
        val progress = ImportProgress.of(1)

        assertFalse(progress.isComplete)
        val finished = progress.advance()
        assertTrue(finished.isComplete)
        assertEquals(1, finished.processed)
        assertEquals(1, finished.total)
        assertEquals(1f, finished.fraction, 0.0001f)
    }

    @Test
    fun threeSelectedImagesFinishOnTheThird() {
        var progress = ImportProgress.of(3)

        repeat(2) { progress = progress.advance() }
        assertFalse(progress.isComplete)

        progress = progress.advance()
        assertTrue(progress.isComplete)
        assertEquals(3, progress.processed)
    }

    @Test
    fun tenSelectedImagesFinishOnTheTenth() {
        var progress = ImportProgress.of(10)

        repeat(9) { progress = progress.advance() }
        assertFalse(progress.isComplete)

        progress = progress.advance()
        assertTrue(progress.isComplete)
        assertEquals(10, progress.processed)
        assertEquals(10, progress.total)
    }

    @Test
    fun progressNeverOvershootsTheSelection() {
        val finished = ImportProgress.of(3)
            .advance()
            .advance()
            .advance()
            .advance()

        assertEquals(3, finished.processed)
        assertEquals(3, finished.total)
        assertEquals(1f, finished.fraction, 0.0001f)
    }

    @Test
    fun theImportScreenIsGoneOnceEveryImageHasBeenHandled() {
        assertTrue(QueueImport.isImportRunning(ImportProgress.of(3)))
        assertTrue(QueueImport.isImportRunning(ImportProgress(2, 3)))
        assertFalse(QueueImport.isImportRunning(ImportProgress(3, 3)))
    }

    @Test
    fun anEmptySelectionIsNeverAnImportInProgress() {
        assertFalse(QueueImport.isImportRunning(null))
        assertFalse(QueueImport.isImportRunning(ImportProgress(0, 0)))
    }

    @Test
    fun theScreenReturnsToTheQueueWhenTheImportIsDone() {
        val running = QueueUiState(importProgress = ImportProgress.of(5))
        assertTrue(running.importing)

        // The last image has been handled and the queue is saved: progress is
        // cleared, so the queue/home content is shown instead of a progress card.
        val finished = QueueUiState(importProgress = null)
        assertFalse(finished.importing)

        // And a progress value that has run out is not treated as running either.
        assertFalse(QueueUiState(importProgress = ImportProgress(5, 5)).importing)
    }

    @Test
    fun aFullImportKeepsEverySelectedImage() {
        val summary = ImportSummary(imported = 3, failed = 0)

        assertEquals(3, summary.selected)
        assertTrue(summary.allImported)
        assertFalse(summary.hasFailures)
    }

    @Test
    fun aPartialImportReportsBothCounts() {
        val summary = ImportSummary(imported = 4, failed = 1)

        assertEquals(5, summary.selected)
        assertFalse(summary.allImported)
        assertTrue(summary.hasFailures)
        assertEquals(4, summary.imported)
        assertEquals(1, summary.failed)
    }

    @Test
    fun anImportWhereNothingCouldBeCopiedIsStillReported() {
        val summary = ImportSummary(imported = 0, failed = 5)

        assertEquals(5, summary.selected)
        assertEquals(0, summary.imported)
        assertTrue(summary.hasFailures)
    }

    @Test
    fun theDoublePaymentWarningOnlyAppearsForAChosenItem() {
        assertFalse(QueueUiState().reShareConfirmationVisible)
        assertNull(QueueUiState().reShareItemId)
        assertTrue(QueueUiState(reShareItemId = "item-1").reShareConfirmationVisible)
    }
}
