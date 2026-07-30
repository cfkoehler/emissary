package emissary.core;

import emissary.directory.DirectoryEntry;
import emissary.place.IServiceProviderPlace;
import emissary.place.ServiceProviderPlace;
import emissary.test.core.junit5.UnitTest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 *
 */
class HDMobileAgentTest extends UnitTest {

    public HDMobileAgentTest() {}

    @Test
    void testAtPlaceHDNull() throws Exception {
        final SimplePlace place = new SimplePlace("emissary.core.FakePlace.cfg");
        ArrayList<IBaseDataObject> children = new ArrayList<>();
        IBaseDataObject ibdo = DataObjectFactory.getInstance(new byte[] {}, "testFile", "someFormFileType");
        children.add(ibdo);
        children.add(null);
        place.setReturnCollection(children);
        HDMobileAgent ma = new HDMobileAgent();
        List<IBaseDataObject> ret = ma.atPlaceHD(place, Collections.emptyList());
        assertEquals(1, ret.size());

        children.clear();
        children.add(ibdo);
        children.add(ibdo);
        ret = ma.atPlaceHD(place, Collections.emptyList());
        assertEquals(2, ret.size());
    }

    @Test
    void testAgentControlStopsWhenTimeToQuit() throws Exception {
        final SimplePlace place = new SimplePlace("emissary.core.FakePlace.cfg");
        final LoopingAgent agent = new LoopingAgent(3);
        try {
            agent.addPayload(DataObjectFactory.getInstance(new byte[] {}, "testFile", "UNKNOWN"));

            // getNextKey always routes back to the same local place, so the only way out of
            // agentControl is the timeToQuit check in the loop condition
            assertTimeoutPreemptively(Duration.ofSeconds(30), () -> agent.agentControl(place),
                    "agentControl must return once timeToQuit is set");

            assertEquals(3, agent.placeVisits, "agentControl must stop visiting places as soon as timeToQuit is set");
        } finally {
            place.shutDown();
        }
    }

    /**
     * Agent whose itinerary never ends, and which asks to be killed once it has visited a place {@code quitAfter} times.
     */
    static final class LoopingAgent extends HDMobileAgent {

        private static final long serialVersionUID = 1L;

        private final int quitAfter;
        transient int placeVisits = 0;

        LoopingAgent(final int quitAfter) {
            this.quitAfter = quitAfter;
        }

        @Override
        protected List<IBaseDataObject> atPlaceHD(final IServiceProviderPlace place, final List<IBaseDataObject> payloadListArg) {
            if (++this.placeVisits >= this.quitAfter) {
                killAgentAsync();
            }
            return Collections.emptyList();
        }

        @Override
        protected DirectoryEntry getNextKey(final IServiceProviderPlace place, final IBaseDataObject payload) {
            return place.getDirectoryEntry();
        }
    }

    static final class SimplePlace extends ServiceProviderPlace {

        private List<IBaseDataObject> children = Collections.emptyList();

        public SimplePlace(String configInfo) throws IOException {
            super(configInfo, "SimplePlace.www.example.com:8001");
        }

        void setReturnCollection(List<IBaseDataObject> children) {
            this.children = children;
        }

        @Override
        public List<IBaseDataObject> agentProcessHeavyDuty(List<IBaseDataObject> payloadListArg) {
            return children;
        }


    }
}
