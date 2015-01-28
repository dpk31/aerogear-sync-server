package org.jboss.aerogear.sync.diffmatchpatch;

import org.junit.Test;

import java.util.Iterator;
import java.util.Queue;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class DiffMatchPatchInMemoryDataStoreTest {

    @Test
    public void getEdits() {
        final String documentId = "12345";
        final String clientId = "client1";
        final DiffMatchPatchInMemoryDataStore dataStore = new DiffMatchPatchInMemoryDataStore();
        final DiffMatchPatchEdit editOne = DiffMatchPatchEdit.withDocumentId(documentId).clientId(clientId).clientVersion(0).build();
        final DiffMatchPatchEdit editTwo = DiffMatchPatchEdit.withDocumentId(documentId).clientId(clientId).clientVersion(1).build();
        dataStore.saveEdits(editOne);
        dataStore.saveEdits(editTwo);
        final Queue<DiffMatchPatchEdit> edits = dataStore.getEdits(documentId, clientId);
        assertThat(edits.size(), is(2));
        final Iterator<DiffMatchPatchEdit> iterator = edits.iterator();
        assertThat(iterator.next().clientVersion(), is(0L));
        assertThat(iterator.next().clientVersion(), is(1L));
    }
}
