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
	
	public void startZip(String prefix) throws Exception {
		setPrefix(prefix);
		StorageManager manager = new StorageManager();
		String bucketName = manager.getTodayBucketName();
		ArrayList<String> list = manager.getUploadedEnclosureFiles(bucketName, getPrefix());
		try {
			downloadFilesToTempFolder(manager, bucketName, list);
			zipDownloadedContent(manager.getZippedEnclosureName(getPrefix()));
			uploadZippedEnclosure(bucketName, manager, manager.getZippedEnclosureName(getPrefix()), new File(manager.getZippedEnclosureName(getPrefix())));
			File fileToDelete = new File(getBaseFolderNameWithEnclosurePrefix());
			deleteFilesFromTemp(fileToDelete);
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

	public void uploadZippedEnclosure(String bucketName, StorageManager manager, String fileName, File fileToUpload) throws Exception {
		manager.uploadMultipartForZip(bucketName, fileName, fileToUpload);
//		manager.createFile(bucketName, fileToUpload, fileName);
	}

	private void zipDownloadedContent(String zippedFileName) throws IOException {
		String sourceFile = getBaseFolderNameWithEnclosurePrefix();
		FileOutputStream fos = new FileOutputStream(getBaseFolderNameWithZipPrefix(zippedFileName));
		ZipOutputStream zipOut = new ZipOutputStream(fos);
		File fileToZip = new File(sourceFile);

		zipFile(fileToZip, fileToZip.getName(), zipOut);
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
		InputStream reader = null;
		OutputStream writer = null;
		BufferedInputStream readerBufferedInputStream = null;
		try {
			readerBufferedInputStream = new BufferedInputStream(object.getObjectContent());
			reader = readerBufferedInputStream;
			String baseFolderName = getBaseFolderName();
			File file = new File(baseFolderName + fileName);
			file.getParentFile().mkdirs();
			try(FileOutputStream fileOutputStream = new FileOutputStream(file)){
				
				try(BufferedOutputStream writerBufferedOutputStream = new BufferedOutputStream(fileOutputStream)){
					writer = writerBufferedOutputStream;
					
				}catch (Exception e) {
					throw new WorkerException("Error During WriteFile");
				}
			}catch (Exception e) {
				throw new WorkerException("Error During WriteFile");
			}
			int read = -1;
			while ((read = reader.read()) != -1) {
				writer.write(read);
			}
		} catch (Exception e) {
			throw new WorkerException("Error During WriteFile");
		}finally {
			
			if(readerBufferedInputStream != null) {
				readerBufferedInputStream.close();
			}
			
			if(writer != null) {
				writer.flush();
				writer.close();
			}
			
			if(reader != null) {
				reader.close();
			}
		}
		
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