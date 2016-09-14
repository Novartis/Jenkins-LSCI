/*
Copyright 2016 Novartis Institutes for BioMedical Research Inc.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

/* 
 * Given a pipeline and an image list it will extract all required pipeline annotations in a property file
 * Re-writes a given CellProfiler pipeline so that input references are remapped
 * It auto-adapts for either LoadData or LoadImages entry modules
 * LoadData Input Location and ImageList names are replaced from ImageList
 * LoadData required grouping Metadata are cross validated (LoadData vs ImageList)
 * Required images are cross validated vs headers in ImageList
 * ImageList can come from user input or the pipeline itself
 * ImageList can be defined from canonical path or a URL (we then auto-download the file from URL)
 * Can handle large image lists without memory errors
 * Author Ioannis K. Moutsatsos
 * Since Jan-21-2013
 * Last Updated AUG-03-2016
*/

import java.text.SimpleDateFormat
import pipelineUtils.*

def cli = new CliBuilder(usage:'autoReviewPipeline.groovy -p [-i-e]')
cli.with{
	h longOpt: 'help', 'Extracts pipeline metadata to a properties file'
	i longOpt: 'input', args:1, argName: 'input image source', 'Complete path to an image list or folder'
	p longOpt: 'pipeline', args:1, argName: 'module pipeline', 'Complete path to CellProfiler pipeline', required:true
        g longOpt: 'grouping', args:1, argName: 'metadata for grouping', 'Comma separated list of metadata fields for grouping'
	d longOpt: 'destination', args:1, argName: 'destination folder', 'Output folder for generated cp pipeline file'
	}
def options = cli.parse(args)

if (!options) return

if(options.h){
   cli.usage()
   return
   }

def env = System.getenv() //also get the environment
   

/* initial template bindings, they get expanded from header of csv test definition */
	def binding=[
			svnVersion:'\\\'10826\\\'',
			dl:'\\',
			dx:'\\x',
			pythonEscapedChars:'\\x5B\\x5D',
			outline:'Outline',
			colorMap:'Default',
			mn:0, //module number is this +1
			mt:0,
			testName:'LoadData',
			modulePipeline:options.p
	        ]

/* Alternate paths are taken depending on whether pipeline starts with the LoadImages or LoadData modules
 * LoadImages requires an image folder while LoadData requires an ImageList
 */

def imageFolder=null
def imageListFile=null
def imageListCpPath=null
def imageFolderCpPath=null
def imageSource=null //file from canonical path, URL or  pipeline LoadData module
def reqInputIsFolder=true // By default assume the Entry module requires input from an image folder (LoadImages)
def requiredImages=[]
def props = new Properties() //property file for imageList metadata
def wavelengthMap=[:] //map of wavelength channels to pipeline objects of interest
def pipelineModules=[] //list of pipeline modules can be used for various validations

/*Open annotated Pipeline File and do a brief existence and format QC */
	def cpPipeline= new File(options.p)
	assert cpPipeline.exists()
    def autoLabel=cpPipeline.name.lastIndexOf('.'). with { it != -1 ? cpPipeline.name[0 ..<it] : cpPipeline.name}
	
	cpPipelineLines=cpPipeline.readLines()
	header=cpPipelineLines[0].split(/,/)
	assert header[0]=='CellProfiler Pipeline: http://www.cellprofiler.org'
    def pipelineVersion=cpPipelineLines[1].split(/:/)[1]
    props.setProperty('PIPELINE_VERSION',pipelineVersion)
    setPipelineBoundaries(binding, pipelineVersion)   //set pipeline guideposts from version
   //trunk or release: release has a date rather than sv revision
//TODO replace with binding variables
    def pipelineType=cpPipelineLines[binding.line_pipelineType].split(/:/)[0]
    props.setProperty('PIPELINE_TYPE',pipelineType)
    def pipelineRevision=cpPipelineLines[binding.line_pipelineRevision].split(/:/)[1]
    props.setProperty('PIPELINE_REVISION',pipelineRevision)

//Assign output folder if user did not 
	if(options.d){
	_outputFolder=options.d
	}else
	{
	_outputFolder=cpPipeline.parent //output by default is generated in the folder of the provided pipeline
}

//Setup modules output
    moduleFileName=_outputFolder+"/"+'pipelineModules.csv'
    moduleFile = new File(moduleFileName)	
    pipelineModules=getPipelineModules(cpPipelineLines)
    listToTable(true,true,['MODULE_NUM','MODULE_NAME'],pipelineModules,moduleFile)
    props.setProperty('MODULE_USED',pipelineModules.toString().replace('[','').replace(']',''))
//    props.setProperty('PIPELINE_AUTOLABEL',autoLabel.replace(' ','_'))
    
    //set properties from environment
    if (env['AUTHOR_COMMENTS']){
    props.setProperty('PIPELINE_COMMENTS', env['AUTHOR_COMMENTS'])
    }else{
    props.setProperty('PIPELINE_COMMENTS', 'None Provided')
    }
    if (env['PIPELINE_DESCRIPTION']){
    props.setProperty('PIPELINE_DESCRIPTION', env['PIPELINE_DESCRIPTION'])
    }else{
    props.setProperty('PIPELINE_DESCRIPTION', 'None Provided')
    }
    
    props.setProperty('PIPELINE_LABEL', env['PIPELINE_LABEL'])
    props.setProperty('PIPELINE_AUTHOR', (env['AUTHOR'])!=null?env['AUTHOR']:'anonymous')   
    props.setProperty('BUILD_NUMBER', env['BUILD_NUMBER'])
    props.setProperty('BUILD_ID', env['BUILD_ID'])
    props.setProperty('BUILD_TAG', env['BUILD_TAG'])

    //note logic- If user did not provide an imageList we try to extract it from pipeline
    if (options.'i'){
        println 'Image source provided by user'
        imageSource= getImageSource(options.i, cpPipeline, binding)
    }else{
        println 'Auto-discovering image source from pipeline file'
        imageSource= getImageSource('pipeline', cpPipeline, binding)
    }
    binding.'imageSource'=imageSource
    assert imageSource.exists()
    println '\tWAVELENGTH_MAP: '+(getWavelengthMap(binding.imageSource).toString().replace('[','').replace(']','') )
	
/* Inspect the pipeline and determine which entry module is used
 * Assert that the image input matches what the module expects (imagelist file or folder)
 * if we do not detect an image/data loading entry module we throw an exception
*/
def entryModule=cpPipelineLines[binding.line_pipelineStart].split(/:/)
if(entryModule[0].startsWith("LoadData")){ //Load Images from ImageList
		println "Entry Module: LoadData"
//        println(getWavelengthMap(binding.imageSource).toString().replace('[','').replace(']','') )
        props.setProperty('MODULE_FOR_IMAGE_LOADING','LoadData')
        wavelengthMap= getWavelengthMap(binding.imageSource)
        props.setProperty('CHANNEL_NUMBER_OF',wavelengthMap.size().toString())
        props.setProperty('CHANNEL_WAVELENGTH_MAP', wavelengthMap.toString().replace('[','').replace(']',''))
        props.setProperty('CHANNEL_WAVELENGTHS',wavelengthMap.keySet().toString().replace('[','').replace(']',''))
		reqInputIsFolder=false //modify default assumption
		//		verify that input is image list file
		try{assert imageSource.isFile(): "ERROR: Pipeline requires an image list instead of image folder"
			}catch(AssertionError e){println e.getMessage();throw new RuntimeException()}
		requiredImages=getRequiredImages(cpPipelineLines).unique()
		println "Required images: $requiredImages"
        println "Image Modules: $pipelineModules"
        props
		binding.'requiredImages'=requiredImages		
	} else { 
		if(entryModule[0].startsWith("LoadImages")){ //Load Images from Folder
		println "Entry Module: LoadImages"
		//		verify that input is folder
		try{assert imageSource.isDirectory(): "ERROR: Pipeline requires an image folder instead of image list"
			}catch(AssertionError e){println e.getMessage();throw new RuntimeException()}
			}else{
			throw new RuntimeException('Pipeline is missing LOAD module!!')}
		}

if(reqInputIsFolder){ //Load Images from Folder
	imageFolder=imageSource.canonicalPath
//println "Using Image Folder: $imageFolder"
	binding.'imageFolder'=imageFolder
	imageListFolder= new File(imageFolder)
	assert imageListFolder.exists()
	imageFolderCpPath=imageFolder.replace('\\','\\\\').replace(':','\\x3A')
	println "reading files from directory '$imageListFolder'"
	// tif  files in current folder
	files = imageListFolder.listFiles().grep(~/.*tif$/)
	props.setProperty('IMAGELIST_SIZE', files.size().toString())
	props.setProperty('IMAGELIST_FOLDER',imageFolder)
	props.setProperty('IMAGELIST_PATH',imageListFolder.canonicalPath)
	props.setProperty('REQUIRES_FOLDER','true')	

}else{        
/*Open ImageList File and do a brief format QC */
	imageListFile= imageSource //new File(options.i)
	assert imageListFile.exists()
//println "Using Image List: ${imageListFile.name}"
	imageListFileLines=imageSource.readLines()
	imageFolder=getImageFolder(binding)
	imageFolderCpPath=imageFolder.replace('\\','\\\\').replace(':','\\x3A')
	imageListCpPath= imageSource.parent.replace('\\','\\\\').replace(':','\\x3A')
	//set some properties
	props.setProperty('IMAGELIST_SIZE', (imageListFileLines.size()-1).toString())
	props.setProperty('IMAGELIST_FOLDER',imageFolder)
	props.setProperty('IMAGELIST_PATH',imageListFile.canonicalPath)
	props.setProperty('REQUIRES_FOLDER','false')	
	}
	
//Assign output folder if user did not 
//	if(options.d){
//	_outputFolder=options.d
//	}else
//	{
//	_outputFolder=cpPipeline.parent //output by default is generated in the folder of the provided pipeline
//}

// CONVENTION: obtain a name for the output pipeline from formula inputPipelineName_timeStamp_val.cp
def valName=cpPipeline.name.lastIndexOf('.'). with { it != -1 ? cpPipeline.name[0 ..<it] : cpPipeline.name}
//	pipelineVal=_outputFolder+"/"+"${valName}_${getTnow()}_val.cp"
	pipelineVal=_outputFolder+"/"+'CP_pipeline_val.cp'
	pipelineValFile = new File(pipelineVal)
    //if file exists remove it
    if (pipelineValFile.exists()){
        pipelineValFile.delete()
    }

//setup name for properties file
propName=_outputFolder+"/"+'autoReviewPipeline.properties'
propertiesFile = new File(propName)	

if(reqInputIsFolder){ //Load Images from Folder
inputLocation=cpPipelineLines[10].split(/:/)
	cpPipelineLines[10]="${inputLocation[0]}:${replaceWithJenkinsJobParam(inputLocation[1], imageFolderCpPath, 'LOADDATA_INPUTLOCATION', binding)}"

}else{//LoadImages from Imagelist

/*Having Confirmed existence of LoadData module we can assume module standard structure 
 * line  6: contains input location
 * line  7: name of csv image list file
 * line  9: base image location
 * line 12: group images flag
 * line 13: fields for grouping
 * we updated lines from provided imageList parameters
*/	
//	def imageListFile= new File(options.i)
	inputLocation=cpPipelineLines[binding.line_inputLocation].split(/:/)
	cpPipelineLines[binding.line_inputLocation]="${inputLocation[0]}:${replaceWithJenkinsJobParam(inputLocation[1], imageListCpPath, 'LOADDATA_INPUTLOCATION', binding)}"
	
	nameOfFile=cpPipelineLines[binding.line_nameOfFile].split(/:/)
	println "URL of the file:${env['BUILD_URL']} ${binding.dx+'3A'}"
	cpPipelineLines[binding.line_nameOfFile]="    URL of the file:${env['BUILD_URL'].replace(':',binding.dx+'3A')}/artifact/example_image_list.csv"
	
	baseImageLocation=cpPipelineLines[binding.line_baseImageLocation].split(/:/)
	cpPipelineLines[binding.line_baseImageLocation]="${baseImageLocation[0]}:${replaceWithJenkinsJobParam(baseImageLocation[1], imageFolderCpPath, 'LOADDATA_BASEIMAGE', binding)}"
	
	//Next, just checking that required metadata are in imageList
    // TODO implement case for user supplied metadata fields, Remove and Trim white space
    groupFlagOption=cpPipelineLines[binding.line_groupFlagOption].split(/:/)
    groupMetadata=	cpPipelineLines[binding.line_groupMetadata].split(/:/)
    props.setProperty('GROUPBY_META',groupFlagOption[1])
    if (groupFlagOption[1]=='Yes' && groupMetadata.size()>1 ){
    props.setProperty('GROUPBY_TAGS',groupMetadata[1])
    }else{
    props.setProperty('GROUPBY_META','Yes, but no metadata tags selected')
    }
    //add pipeline view and download link properties
    props.setProperty('PIPELINE_VIEW.contributed',"${env['BUILD_URL']}artifact/user_pipeline.cp/*view*/")
    props.setProperty('PIPELINE_VIEW.adapted',"${env['BUILD_URL']}artifact/CP_pipeline_val.cp/*view*/")
    props.setProperty('download.pipeline.contributed',"${env['BUILD_URL']}artifact/user_pipeline.cp")
    props.setProperty('download.pipeline.adapted',"${env['BUILD_URL']}artifact/CP_pipeline_val.cp")
    props.setProperty('download.imagelist.example',"${env['BUILD_URL']}artifact/example_image_list.csv")
    //If user assigned grouping fields we modify original pipeline grouping options
    if(options.g){
        cpPipelineLines[binding.line_groupFlagOption]="${groupFlagOption[0]}:Yes"
        userGroupMeta=options.g
        println("User grouping fields: $userGroupMeta")
        cpPipelineLines[binding.line_groupMetadata]="${groupMetadata[0]}:${replaceWithJenkinsJobParam(userGroupMeta, userGroupMeta,'LOADDATA_GROUPMETA', binding)}"
//        return
    }  else{

    groupMetadata=	cpPipelineLines[binding.line_groupMetadata].split(/:/)  // we now work with a possibly modified metadata grouping
	if (groupMetadata.length>1){
	//we verify the existence of metadata without altering it
	cpPipelineLines[binding.line_groupMetadata]="${groupMetadata[0]}:${replaceWithJenkinsJobParam(groupMetadata[1], groupMetadata[1],'LOADDATA_GROUPMETA', binding)}"
	}else{
	println "Metadata not used for grouping"
	}
        } //end else
	}
	
/* We finally save buffer to output file */
cpPipelineLines.each{line ->
pipelineValFile<<line+'\n'
}

/* and save imageList properties file */
props.store(propertiesFile.newWriter(), null)

println "Validated Pipeline:\n\t ${pipelineValFile.canonicalPath}"
println "Pipeline properties:\n\t ${propertiesFile.canonicalPath}"	
	
/*--------------------------------------------METHODS--------------------------------------------*/	
/*
set bindings for the pipeline structure based on pipeline version
 */
def setPipelineBoundaries(HashMap binding, cpPipelineVersion){
    println "Pipeline Version Reported as: $cpPipelineVersion"
    //start line varies by CellProfiler version
    switch(cpPipelineVersion){
        case ["1","2"]:
            println "Detected Pipeline Version as: $cpPipelineVersion"
            binding.'pipelineVersion'=2
            binding.'line_pipelineStart'=4
            binding.'line_pipelineType'= 2
            binding.'line_pipelineRevision'= 2
            binding.'line_inputLocation'=5
            binding.'line_nameOfFile'= 6
            binding.'line_baseImageLocation'= 8
            binding.'line_groupFlagOption'=11
            binding.'line_groupMetadata'= 12
            break
        case "3":
            println "Detected Pipeline Version as: $cpPipelineVersion"
            binding.'pipelineVersion'=3
            binding.'line_pipelineStart'=7
            binding.'line_pipelineType'= 2
            binding.'line_pipelineRevision'= 2
            binding.'line_inputLocation'=8
            binding.'line_nameOfFile'= 9
            binding.'line_baseImageLocation'= 11
            binding.'line_groupFlagOption'=14
            binding.'line_groupMetadata'= 15
            break
    }

}
/* Returns a list of Images required by a CellProfiler pipeline */

def getRequiredImages(pipelineLineList){
/*iterate all lines and determine required inputs */
imageFileNames=[] //a list to collect the required Image Names
pipelineLineList.each{ modLine->
	def annotatedModuleLine=new AnnotatedModuleLine(modLine, 'Mx')
	switch(annotatedModuleLine.typeForName)
			{
			case"IMAGE":
				reqFile=annotatedModuleLine.getLineValue()
				if(reqFile!='None'){
				 imageFileNames +=reqFile
				}
//				println 'Module INPUT ->'+ annotatedModuleLine.thisLine
				break
			}

	} //end each
 return imageFileNames
}
/**
 * Returns a unique list of pipeline modules
 * @param pipelineLineList
 */
def getPipelineModules(pipelineLineList){
    pipelineModules=[] // a list to collect pipeline modules
    pipelineLineList.each{ modLine->
        def annotatedModuleLine=new AnnotatedModuleLine(modLine, 'Mx')
        switch(annotatedModuleLine.lineType)
        {
            case"MODULE":
                modName=annotatedModuleLine.'moduleName'
                pipelineModules +=modName
//				println 'Module ->'+ annotatedModuleLine.thisLine
                break
        }

    } //end each
    return pipelineModules
}
/* Returns the image list for the pipeline
 * from a user provided path or the pipeline build-in path
 * The path could be a URL or a a UNC path
 */
def getImageSource(imageListPath,cpPipeline,binding) {
    if (imageListPath.toString().startsWith('http')){
        //dealing with a URL based list we copy imageList to a local file
        def url = imageListPath
        def file = new File("${cpPipeline.parent}/example_image_list.csv").newOutputStream()
        //file << new URL(url).openStream()
        l=0 //line to get from URL
        listUrl= new URL(url)
        listUrl.eachLine{
            if (l<6){
                file<<it
                file<< '\n'
                l++
            }else{
                return
            }

        }
        file.close()
        return new File("${cpPipeline.parent}/example_image_list.csv")
    }else{
        //have a standard Path and must check
        if(imageListPath =='pipeline'){
            //must read from the LoadData module of pipeline
            return getImageSourceFromPipeline(cpPipeline,binding)
        }else{
            return new File(imageListPath)
        }
    }

}
    /* Returns the image source by parsing the pipeline itself
    Image source must therefore by accessible to the server
     */
def getImageSourceFromPipeline(pipeline,binding){
    def url=''
    cpPipelineLines=pipeline.readLines()
    header=cpPipelineLines[0].split(/,/)
    assert header[0]=='CellProfiler Pipeline: http://www.cellprofiler.org'
    def entryModule=cpPipelineLines[binding.line_pipelineStart].split(/:/)
    if(entryModule[0].startsWith("LoadData")){ //Load Images from ImageList
             inputDataLocation=cpPipelineLines[binding.line_inputLocation].split(/:/)
            println "InputDataLocation: $inputDataLocation"
            if (inputDataLocation[1].startsWith('URL')){
                urlOfFile=cpPipelineLines[binding.line_nameOfFile].split(/:/)
                url=urlOfFile[1].replace('\\x3A',':')
                println "Image List from URL..."
                println "\tURL: $url"
            }
        if (inputDataLocation[1].startsWith('Elsewhere')){
            pathOfFile=inputDataLocation[1].split(/x7C/)[1]
            nameOfFile=cpPipelineLines[binding.line_nameOfFile].split(/:/)
            fileName=nameOfFile[1]
            url="$pathOfFile/$fileName"
            println "Image List from PATH..."
            println "\tPATH: $url"
        }

    }

    return getImageSource(url, pipeline, binding)
}


/* Returns the folder where images are stored
 * by examining the imageList file
 */
def getImageFolder(HashMap binding){
//open image list file and read first image record
	File imageListFile=binding.imageSource
    	imageListParams=getCSVparams(imageListFile,1)
	def imageF=imageListParams.find{it.key.contains('PathName') }.value.replace('\"','').replace('\\','/')
	println "ImageFolderPath: $imageF \n"
	return imageF
}

/* returns the Wavelength Map from the Image list
    Maps BioObjects to imaging channels
    updated to handle raw CP exported image list
 */

def getWavelengthMap(File imageListFile){
//open image list file and read first image record
    listColumns=getCSVparams(imageListFile,1)
    def wvMap=[:]
//	extract wavelength object relations
    listColumns.keySet().each { testKey->
    if (testKey.toString().contains('FileName')){
        ifn=testKey.toString().split('_')
    	bio=testKey.toString().replace('Image_','').replace('FileName_','')
        img=listColumns."${testKey}".split('-')
        wv=img[(img.size()-1)].split('\\)')[0].trim()
        wvMap[wv]=bio
    }

    }
    return wvMap
}
	        
/* Returns a parameter map from a CSV File
 * Reads CSV file header to create map keys
 * Reads CSV line n to get corresponding key values.
 * AUG-21-2013 Modified to use the readLineN method. Now handles large image lists w/o memory errors
*/
def getCSVparams(File paramsFile, fromLine){
	assert paramsFile.exists()
    paramsList =readLineN(paramsFile,1).split(/,/)
    paramsValues =readLineN(paramsFile,fromLine+1).split(/,/)
	csvParamMap= new HashMap()
	tcol=0
	
	paramsList.each{pl->
	csvParamMap."${pl.replace('\"','')}"=paramsValues[tcol]
	tcol++
	}
	return csvParamMap		
}

def String readLineN( File f, int lineNo ) {
    f.withReader { r ->
        ret = ''
        lineNo.times {
            ret = r.readLine()
        }
        ret
    }
}

/* Replaces the original path with a URL to the uploaded image list
 * also asserts that the required pipeline metadata can be found in the imageList
*/
 
def replaceWithJenkinsJobParam(original, param, modParamType, binding) {
/* switch on the type of an AnnotatedModuleLine, true/false refer to the TEST state */	
	switch(modParamType)
			{
			case"LOADDATA_INPUTLOCATION":
				return "URL${binding.dx}7C" //replace file location with URL from Jenkins server
				break
			case"LOADDATA_NAMEOFFILE":
				return param
				break
			case"LOADDATA_BASEIMAGE":
				return "None${binding.dx}7C"
				break
			case"LOADDATA_GROUPMETA":
				metaColumns=getCSVparams(binding.imageSource,1)
				loadDataMeta=original.split(/,/)
				loadDataMeta.each{metaKey ->
				testKey="Metadata_$metaKey"
//				The conversion of testKey toString is required! Otherwise assertion breaks--See http://jira.codehaus.org/browse/GROOVY-1768
println metaColumns.keySet()
				try{assert metaColumns.keySet().contains(testKey.toString()), "ERROR: Missing Required Metadata: $testKey"
					}catch(AssertionError e){println e.getMessage();System.exit(0)}
				println "\t Required metadata:${testKey}"
				}
				
				//now check required images
				binding.'requiredImages'.each{requiredImage->
				testImage="Image_FileName_$requiredImage"
				try{assert metaColumns.keySet().contains(testImage.toString()), "ERROR: Missing Required Image: Image_FileName_$requiredImage"
//					}catch(AssertionError e){println e.getMessage();System.exit(0)}
//                    println "\t Required image:${testImage}"
                    println "-++- FOUND in IMAGE LIST:$requiredImage <as>:${testImage}"
                }catch(AssertionError e){println "-??- MISSING or PIPELINE GENERATED:$requiredImage"}
//				println "\t Required image:${testImage}"
				
				}
				return param
				break				
			}
}
   
/*
 * Return the current time as a string.
 * This format is compatible with use in filenames on windows and is used as a part to the name of the CSV files
 */
 def getTnow(){
	 def DATE_FORMAT_NOW = "yyyy-MM-dd_HH_mm_ss"
	 def cal = Calendar.getInstance()
	 def sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
	 sdf.format(cal.getTime())
 
 }
 
/*
a function to create a delimited table file from a groovy list
    separator is used to delimit the list elements-default is 'comma'
    headlessList flag true, unless the list[0] element can be used as a header. Then false 
    theHeader list provides column headings if not defined in list[0] element
    tableFile the file to write list to
*/

def listToTable(separator = ',', Boolean headLessList, Boolean withIndex, List<String> theHeader, List theList, File tableFile) {
    tHeader = ''
    tfWriter = tableFile.newWriter(false)
    if (headLessList && theHeader != null) {
        tHeader = theHeader
        theHeadlessList = theList
    } else {
        println 'Assigning header from list'
        tHeader = theList[0]
        theHeadlessList = theList - [theList[0]]
    }

    println tHeader.join(separator)
    tfWriter << tHeader.join(separator) + '\n'
    if (withIndex) {
        if (theList[0].class.simpleName == 'ArrayList') {
            assert tHeader.size() == (theList[0]).size() + 1
            theHeadlessList.eachWithIndex { val, inx ->
                println "${inx + 1}$separator${val.join(separator)}"
                tfWriter << "${inx + 1}$separator${val.join(separator)}\n"
            }
        } else {
            assert tHeader.size() == 2
            theHeadlessList.eachWithIndex { val, inx ->
                println "${inx + 1}$separator${val}"
                tfWriter << "${inx + 1}$separator${val}\n"
            }
        }
    } else {
        //Actions for no index column
        if (theList[0].class.simpleName == 'ArrayList') {
            assert tHeader.size() == theList[0].size()
            theHeadlessList.each {
                println "${it.join(separator)}"
                tfWriter << "${it.join(separator)}\n"
            }//end each
        } else {
            assert tHeader.size() == 1
            theHeadlessList.each {
                println "$it"
                tfWriter << "$it\n"
            }
        }

    }//end else withIndex
    tfWriter.flush()
    tfWriter.close()
    return tableFile
}//end function
