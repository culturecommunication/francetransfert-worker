package cleanup;

import fr.gouv.culture.francetransfert.FranceTransfertWorkerStarter;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FranceTransfertWorkerStarter.class)
public class CleanUpServicesTest {

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void cleanUpTests() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }
}
