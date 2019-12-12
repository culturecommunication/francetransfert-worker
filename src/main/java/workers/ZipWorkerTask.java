package workers;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.opengroup.mc.francetransfert.api.francetransfert_storage_api.StorageManager;

public class ZipWorkerTask implements Runnable {

	String prefix;
	String tmpFolderPath = "C:\\Users\\EXT_HTA37\\tmp\\";

	public ZipWorkerTask(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public void run() {
		StorageManager manager = new StorageManager();
		String bucketName = manager.getTodayBucketName();
		ArrayList<String> list = manager.getUploadedEnclosureFiles(bucketName, getPrefix());
		try {
			downloadFilesToTempFolder(manager, bucketName, list);
			zipDownloadedContent(manager.getZippedEnclosureName(getPrefix()));
			uploadZippedEnclosure();
			File fileToDelete = new File(getBaseFolderNameWithEnclosurePrefix());
			deleteFilesFromTemp(fileToDelete);
			deleteFilesFromOSU(manager, bucketName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void deleteFilesFromOSU(StorageManager manager, String bucketName) {
		manager.deleteFilesWithPrefix(bucketName, getPrefix());
	}

	private void deleteFilesFromTemp(File file) {
		for (File subFile : file.listFiles()) {
			if (subFile.isDirectory()) {
				deleteFilesFromTemp(subFile);
			} else {
				subFile.delete();
			}
		}
		file.delete();
	}

	private void uploadZippedEnclosure() {
		// TODO Auto-generated method stub

	}

	private void zipDownloadedContent(String zippedFileName) throws IOException {
		String sourceFile = getBaseFolderNameWithEnclosurePrefix();
		FileOutputStream fos = new FileOutputStream(getBaseFolderNameWithEnclosurePrefix() + ".zip");
		ZipOutputStream zipOut = new ZipOutputStream(fos);
		File fileToZip = new File(sourceFile);

		zipFile(fileToZip, zippedFileName, zipOut);
		zipOut.flush();
		zipOut.close();
		fos.flush();
		fos.close();
	}

	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
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
		FileInputStream fis = new FileInputStream(fileToZip);
		ZipEntry zipEntry = new ZipEntry(fileName);
		zipOut.putNextEntry(zipEntry);
		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zipOut.write(bytes, 0, length);
		}
		fis.close();
	}

	private void downloadFilesToTempFolder(StorageManager manager, String bucketName, ArrayList<String> list) {
		try {
			for (String fileName : list) {
				S3Object object = manager.getObjectByName(bucketName, fileName);
				if (!fileName.endsWith(File.separator)) {
					writeFile(object, fileName);
				}
			}
		} catch (Exception e) {

		}
	}

	public void writeFile(S3Object object, String fileName) throws IOException {
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

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

}
