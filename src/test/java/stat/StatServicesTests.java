package stat;

import fr.gouv.culture.francetransfert.FranceTransfertWorkerStarter;
import fr.gouv.culture.francetransfert.services.stat.StatServices;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FranceTransfertWorkerStarter.class)
public class StatServicesTests {

    @Autowired
    private StatServices statServices;

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void shouldSaveDataTest() throws Exception {
        //given
        String enclosureId = "8619c5ac-f8d4-4d6b-930f-e07a27e9e72d";
        //when
        boolean isSaved = statServices.saveData(enclosureId);
        //then
        Assert.assertTrue(isSaved);
    }


    @After
    public void tearDown() throws Exception {

    }



}
