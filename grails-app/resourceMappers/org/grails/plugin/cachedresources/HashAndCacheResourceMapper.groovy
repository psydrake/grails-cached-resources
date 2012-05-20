package org.grails.plugin.cachedresources

import java.nio.channels.*
import org.codehaus.groovy.grails.plugins.codecs.SHA256BytesCodec
import org.grails.plugin.cachedresources.util.Base62
import org.grails.plugin.resource.mapper.MapperPhase

class HashAndCacheResourceMapper {

    static phase = MapperPhase.RENAMING
    
    def cacheHeadersService
    
    def resourceService
    
    /**
     * Rename the file to a hash of it's contents, and set caching headers 
     */
    def map(resource, config) {
        if (log.debugEnabled) {
            log.debug "Hashing resources to unique names..."
        }

        resource.processedFile = renameToHashOfContents(resource.processedFile, resource.processedFileExtension)
        resource.updateActualUrlFromProcessedFile()
        
        // Do all the horrible cache header stuff
        resource.requestProcessors << { req, resp ->
            if (log.debugEnabled) {
                log.debug "Setting caching headers on ${req.requestURI}"
            }
            cacheHeadersService.cache resp, [neverExpires: true, shared: true]
        }
    }
    
    /**
     * Returns the config object under 'grails.resources'
     */
    ConfigObject getConfig() {
        grailsApplication.config.cached.resources
    }

    /**
     * Used to retrieve a resources config param, or return the supplied
     * default value if no explicit value was set in config
     */
    def getConfigParamOrDefault(String key, defaultValue) {
        def param = key.tokenize('.').inject(config) { conf, v -> conf[v] }
        if (param instanceof ConfigObject) {
            param.size() == 0 ? defaultValue : param
        } else {
            param
        }
    }
    
    boolean getShortenLinks() {
        resourceService.getConfigParamOrDefault('shorten', true)
    }
    
    boolean getFlattenDirs() {
        resourceService.getConfigParamOrDefault('flatten', true)
    }
    
    /**
     * Renames the given input file in the same directory to be the SHA256 hash of it's contents.
     */
    def renameToHashOfContents(File input, String extension) {
        def newName
        if (shortenLinks) {
            def hash = SHA256BytesCodec.encode(getBytes(input))
            newName = Base62.encode(hash)
        } else {
            newName = SHA256Codec.encode(getBytes(input))
        }
        def parent = flattenDirs ? resourceService.workDir : input.parentFile
        def target = new File(parent, extension ? "${newName}.${extension}" : newName)

        if (target.exists()) {
            assert target.delete()
        }

        if (log.debugEnabled) {
            log.debug "Renaming $input to $target"
        }

        // Rename or copy here?
		//input.renameTo(target)
		copyFile(input, target)
        target
    }
    
    /**
     * File.getBytes() was added in Groovy 1.7.1 - so we roll our own for Grails 1.2 support.
     */
    def getBytes(File f) {
        def bytes = new ByteArrayOutputStream()
        byte[] byteBuffer = new byte[8192]
        def bytesRead
        f.withInputStream {
            while ((bytesRead = it.read(byteBuffer)) != -1) {
                bytes.write(byteBuffer, 0, bytesRead)
            }
            bytes.toByteArray()
        }
    }

	public static void copyFile(File sourceFile, File destFile) throws IOException {
	    if(!destFile.exists()) {
	        destFile.createNewFile();
	    }

	    FileChannel source = null;
	    FileChannel destination = null;
	    try {
	        source = new FileInputStream(sourceFile).getChannel();
	        destination = new FileOutputStream(destFile).getChannel();

	        // previous code: destination.transferFrom(source, 0, source.size());
	        // to avoid infinite loops, should be:
	        long count = 0;
	        long size = source.size();              
	        while((count += destination.transferFrom(source, count, size-count))<size);
	    }
	    finally {
	        if(source != null) {
	            source.close();
	        }
	        if(destination != null) {
	            destination.close();
	        }
	    }
	}
}