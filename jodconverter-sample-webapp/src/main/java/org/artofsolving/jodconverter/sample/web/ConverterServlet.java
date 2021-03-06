package org.artofsolving.jodconverter.sample.web;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.artofsolving.jodconverter.OfficeDocumentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConverterServlet extends HttpServlet {

    private static final long serialVersionUID = -591469426224201748L;

    
	private static final Logger logger = LoggerFactory
			.getLogger(ConverterServlet.class);
//    private final Logger logger = Logger.getLogger(getClass().getName());

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!ServletFileUpload.isMultipartContent(request)) {
        	response.sendError(HttpServletResponse.SC_FORBIDDEN, "only multipart requests are allowed");
        	return;
        }

		WebappContext webappContext = WebappContext.get(getServletContext());
		ServletFileUpload fileUpload = webappContext.getFileUpload();
		OfficeDocumentConverter converter = webappContext.getDocumentConverter();

		// we are only going to do pdf to png
		// String outputExtension = FilenameUtils.getExtension(request.getRequestURI());
		String intermediateExtension = "pdf";
		String finalExtension = "png";
		
		FileItem uploadedFile;
		try {
			uploadedFile = getUploadedFile(fileUpload, request);
		} catch (FileUploadException fileUploadException) {
		    throw new ServletException(fileUploadException);
		}
		if (uploadedFile == null) {
			throw new NullPointerException("uploaded file is null");
		}
        String inputExtension = FilenameUtils.getExtension(uploadedFile.getName());

        String baseName = FilenameUtils.getBaseName(uploadedFile.getName());
        File inputFile = File.createTempFile(baseName, "." + inputExtension);
        writeUploadedFile(uploadedFile, inputFile);
        File outputFile = File.createTempFile(baseName, "." + intermediateExtension);
        File thumbnailFile = File.createTempFile(baseName, "." + finalExtension);

        List<File> files = this.fileList(outputFile, inputFile, thumbnailFile);
        try {
//            DocumentFormat outputFormat = converter.getFormatRegistry().getFormatByExtension(intermediateExtension);
        	long startTime = System.currentTimeMillis();
        	converter.convert(inputFile, outputFile);
        	long conversionTime = System.currentTimeMillis() - startTime;
        	logger.info(String.format("successful conversion: %s [%db] to %s in %dms", inputExtension, inputFile.length(), intermediateExtension, conversionTime));
//        	response.setContentType(outputFormat.getMediaType());
//            response.setHeader("Content-Disposition", "attachment; filename="+ baseName + "." + outputExtension);
            //sendFile(outputFile, response);
            
        	this.doThumb(outputFile, thumbnailFile, baseName, response);
        	long thumbTime = System.currentTimeMillis() - conversionTime;
        	logger.info(String.format("successful thumbnail: %s [%db] to %s in %dms", intermediateExtension, inputFile.length(), finalExtension, thumbTime));
            
            sendFile(thumbnailFile, response);
        } catch (Exception exception) {
            logger.error(String.format("failed conversion: %s [%db] to %s; %s; input file: %s", inputExtension, inputFile.length(), intermediateExtension, exception, inputFile.getName()));
        	throw new ServletException("conversion failed", exception);
        } finally {
        	for (File f : files) {
        		f.delete();
        	}
        }
	}

	private List<File> fileList(File ... files) {
        List<File> list = new ArrayList<File>(3);
        for (File f : files) {
        	list.add(f);
        }
        return list;
	}
	
	@SuppressWarnings("unused")
	private void doThumb(File pdf, File thumb, String baseName, HttpServletResponse response) throws Exception {
		// convert "$outpdf[0]" -flatten -resize "150x150" -colorspace 'rgb' $outjpg 2>/dev/null
		String[] cmd = {
				"convert",
				pdf.getAbsolutePath() + "[0]",
				"-flatten",
				"-resize", "150x150",
				"-colorspace", "rgb",
				thumb.getAbsolutePath()
		};
		
		logger.debug("Converter command: " + cmd);
		
		ProcessBuilder pb = new ProcessBuilder(cmd);
		Process p = pb.start();
		
		// consume both output and stderr
		String stdout = this.consumeStream(p.getInputStream(), "stdout");	// why is it called InputStream when it's really stdout?
		String stderr = this.consumeStream(p.getErrorStream(), "stderr");	

        p.waitFor();
		if (p.exitValue() != 0) {
			logger.warn("Thumbnail exited with non-zero status: " + p.exitValue());
		}
		// assuming all that is fine...
    	response.setContentType("image/png");
        response.setHeader("Content-Disposition", "attachment; filename="+ baseName + ".png");
	}
	
		private String consumeStream(InputStream is, String name) throws Exception {
			StringBuilder sb = new StringBuilder();
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
	        br.close();
			String rv = sb.toString();
			if (rv.length() > 0) {
				logger.warn("Stream '" + name + "': " + rv);
			}
			
			return rv;
		}
	
	private void sendFile(File file, HttpServletResponse response) throws IOException {
		response.setContentLength((int) file.length());
        InputStream inputStream = null;
        try {
        	inputStream = new FileInputStream(file);
            IOUtils.copy(inputStream, response.getOutputStream());
        } finally {
        	IOUtils.closeQuietly(inputStream);
        }
	}

	private void writeUploadedFile(FileItem uploadedFile, File destinationFile) throws ServletException {
        try {
			uploadedFile.write(destinationFile);
		} catch (Exception exception) {
			throw new ServletException("error writing uploaded file", exception);
		}
		uploadedFile.delete();
	}

	private FileItem getUploadedFile(ServletFileUpload fileUpload, HttpServletRequest request) throws FileUploadException {
		@SuppressWarnings("unchecked")
		List<FileItem> fileItems = fileUpload.parseRequest(request);
		for (FileItem fileItem : fileItems) {
			if (!fileItem.isFormField()) {
				return fileItem;
			}
		}
		return null;
	}

}
