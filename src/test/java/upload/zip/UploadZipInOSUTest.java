package upload.zip;

import fr.gouv.culture.francetransfert.FranceTransfertWorkerStarter;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.services.zipworker.ZipWorkerServices;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FranceTransfertWorkerStarter.class)
public class UploadZipInOSUTest {

    @Autowired
    ZipWorkerServices zipWorkerServices;


    @Before
    public void setUp() throws Exception {

    }

//    @Ignore
    @Test
    public void shouldSendMailToRecipientTest() throws Exception {
        //given
        String bucketName = "test-lha-09122019";
        String fileName = "7e4fc103-2517-4dd6-861a-85f1844dd73f.zip";
        String filePath = "C:/test/7e4fc103-2517-4dd6-861a-85f1844dd73f.zip";
        StorageManager manager = new StorageManager();
        //when
manager.getZippedEnclosureName("d895a459-f638-455c-9e81-d2ab4678219f");
//        zipWorkerServices.uploadZippedEnclosure(bucketName, new StorageManager(), fileName, filePath );
//        zipWorkerServices.startZip("d895a459-f638-455c-9e81-d2ab4678219f");

        //then
        filePath = null;
    }


    @After
    public void tearDown() throws Exception {

    }
}