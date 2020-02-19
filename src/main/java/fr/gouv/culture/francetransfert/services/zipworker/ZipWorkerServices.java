package fr.gouv.culture.francetransfert.services.zipworker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.amazonaws.services.s3.model.S3Object;

import fr.gouv.culture.francetransfert.francetransfert_metaload_api.RedisManager;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.enums.RedisQueueEnum;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.StorageManager;
import fr.gouv.culture.francetransfert.security.WorkerException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ZipWorkerServices {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ZipWorkerServices.class);
	
	String prefix;
	
	@Value("${tmp.folder.path}")
    private String tmpFolderPath;

	@Value("${bucket.prefix}")
	private String bucketPrefix;
	
	public void startZip(String prefix) throws Exception {
		setPrefix(prefix);
		StorageManager manager = StorageManager.getInstance();
		manager.getZippedEnclosureName(getPrefix());
		String bucketName = RedisUtils.getBucketName(RedisManager.getInstance(), prefix, bucketPrefix);
		ArrayList<String> list = manager.getUploadedEnclosureFiles(bucketName, getPrefix());
		try {
			LOGGER.info("================================> start download files temp to disk");
			downloadFilesToTempFolder(manager, bucketName, list);
			LOGGER.info("================================> start zip files temp to disk");
			zipDownloadedContent(getPrefix());
			LOGGER.info("================================> start upload zip file temp to OSU");
			uploadZippedEnclosure(bucketName, manager, manager.getZippedEnclosureName(getPrefix())+".zip", getBaseFolderNameWithZipPrefix(getPrefix()));
			File fileToDelete = new File(getBaseFolderNameWithEnclosurePrefix());
			LOGGER.info("================================> start delete zip file in local disk");
			deleteFilesFromTemp(fileToDelete);
			File fileZip = new File(getBaseFolderNameWithZipPrefix(getPrefix()));
			if (!fileZip.delete()) {
				throw  new WorkerException("error delete zip file");
			}
			LOGGER.info("================================> start delete zip file in OSU");
			deleteFilesFromOSU(manager, bucketName);
			notifyEmailWorker();
		} catch (IOException e) {
			LOGGER.error(e.getMessage());
		}
	}

	private void notifyEmailWorker() throws Exception {
		RedisManager.getInstance().publishFT(RedisQueueEnum.MAIL_QUEUE.getValue(), getPrefix());
	}

	private void deleteFilesFromOSU(StorageManager manager, String bucketName) throws Exception {
		manager.deleteFilesWithPrefix(bucketName, getPrefix());
	}

	private void deleteFilesFromTemp(File file) {
		for (File subFile : file.listFiles()) {
			if (subFile.isDirectory()) {
				deleteFilesFromTemp(subFile);
			} else {
				if(!subFile.delete()) {
					LOGGER.error("unable to delete subfile");
				}
			}
		}
		if(!file.delete()) {
			LOGGER.error("unable to delete file");
		}
	}

	public void uploadZippedEnclosure(String bucketName, StorageManager manager, String fileName, String fileZipPath) throws Exception {
		manager.uploadMultipartForZip(bucketName, fileName, fileZipPath);
//		manager.createFile(bucketName, fileToUpload, fileName);
	}

	private void zipDownloadedContent(String zippedFileName) throws IOException {
		String sourceFile = getBaseFolderNameWithEnclosurePrefix();
		FileOutputStream fos = new FileOutputStream(getBaseFolderNameWithZipPrefix(zippedFileName));
		ZipOutputStream zipOut = new ZipOutputStream(fos);
		File fileToZip = new File(sourceFile);
		for (File file :fileToZip.listFiles()) {
			zipFile(file, file.getName(), zipOut);
		}
//		zipFile(fileToZip, fileToZip.getName(), zipOut);
		zipOut.flush();
		zipOut.close();
		fos.flush();
		fos.close();
	}

	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
		FileInputStream fis = null;
		try {
			if (fileToZip.isDirectory()) {
				if (fileName.endsWith(File.separator)) {
					zipOut.putNextEntry(new ZipEntry(fileName));
					zipOut.closeEntry();
				} else {
					zipOut.putNextEntry(new ZipEntry(fileName + File.separator));
					zipOut.closeEntry();
				}
				File[] children = fileToZip.listFiles();
				for (File childFile : children) {
					LOGGER.info("================================> start zip file {} temp to disk", childFile.getName());
					zipFile(childFile, fileName + File.separator + childFile.getName(), zipOut);
				}
				return;
			}
			fis = new FileInputStream(fileToZip);
			ZipEntry zipEntry = new ZipEntry(fileName);
			zipOut.putNextEntry(zipEntry);
			byte[] bytes = new byte[1024];
			int length;
			while ((length = fis.read(bytes)) >= 0) {
				zipOut.write(bytes, 0, length);
			}
		} catch (Exception e) {
			throw new WorkerException("Error During ZipFile");
		}finally {
			if(fis != null) {
				fis.close();
			}
		}
	}

	private void downloadFilesToTempFolder(StorageManager manager, String bucketName, ArrayList<String> list) {
		try {
			for (String fileName : list) {
				S3Object object = manager.getObjectByName(bucketName, fileName);
				if (!fileName.endsWith(File.separator) && !fileName.endsWith("\\") && !fileName.endsWith("/")) {
					writeFile(object, fileName);
				}
			}
		} catch (Exception e) {
			throw new WorkerException("Error During File Dowload from OSU to Temp Folder");
		}
	}

	public void writeFile(S3Object object, String fileName) throws IOException {
		LOGGER.info("================================> start download file : {}  to disk ", fileName);
		InputStream reader = new BufferedInputStream(object.getObjectContent());
		String baseFolderName = getBaseFolderName();
		File file = new File(baseFolderName + fileName);
		file.getParentFile().mkdirs();
		OutputStream writer = new BufferedOutputStream(new FileOutputStream(file));
		int read = -1;
		while ((read = reader.read()) != -1) {
			writer.write(read);
		}
		writer.flush();
		writer.close();
		reader.close();
	}

	private String getBaseFolderName() {
		String baseString = tmpFolderPath;
		return baseString;
	}

	private String getBaseFolderNameWithEnclosurePrefix() {
		String baseString = tmpFolderPath + getPrefix();
		return baseString;
	}
	
	private String getBaseFolderNameWithZipPrefix(String zippedFileName) {
		String baseString = tmpFolderPath + zippedFileName + ".zip";
		return baseString;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

}
