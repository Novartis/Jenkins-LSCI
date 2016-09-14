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

/**
 * A Groovy Wrapper Script for yokoCpImageList.groovy
 * Allows pre-validation and formatting of command line arguments
 * Author: Ioannis K. Moutsatsos
 * Since October 18, 2013
 * Uses Jenkins-CI environment variables to launch the wrapped script
 * Last update August-22-2016
 */
import static groovy.io.FileType.*
def cli = new CliBuilder(usage:'yokoCpImageList_build.groovy -i[-o -a -p -m]')
cli.with{
    h longOpt: 'help', 'Extracts Yokogawa Measurement Metadata'
    i longOpt: 'input', args:1, argName: 'input image source', 'Complete path to a Yokogawa image folder', required:true
    o longOpt: 'output', args:1, argName: 'output file','Output path for saving image list'
    a longOpt: 'append', args:0, argName: 'append to output file', 'Option to append or overwrite the output file'
    p longOpt: 'propertiesFile',args:1,argName:'Properties file path', 'Path to write plateList properties file'
    m longOpt: 'metaMap' , args:1, argName: 'wavelength map', 'Wavelength to Object-Image Map (format is CH01:OBJ1,CH02:OBJ2,CH03:OBJ3)'

}
def options = cli.parse(args)
println "options size: $options.size()"
def _targetFolder= options.i //input folder (may contain subfolders)
def _outputFolder= options.o  // destination folder
def _waveObjMap=options.m // wavelength map

/*  If user did not provide a wavelength map we set the -m argument to null
    Note that a null parameter must be set last in the sequence of parameters when calling the script main()
    Otherwise parameters that follow it are not used
 */

def mCLI_arg=(_waveObjMap==null||_waveObjMap=='none') ? '' : "-m$_waveObjMap"

// measurement data and details require Yokogawa metadata files
def measurementData="MeasurementData.mlf"
def measurementDetail="MeasurementDetail.mrf"

def _appendFlag=false //no csv file header has been written
commonPrefix="imageList"
generatedImgList=_outputFolder+"/"+"${commonPrefix}.csv"
def outFile=new File(generatedImgList)

propName="$_outputFolder./plateList.properties"


/* If target exists we iterate the sub-folders */
def dir = new File(_targetFolder)
println "Checking target folder $_targetFolder"

assert dir.exists()
println "Directory exists"
def dirList=[]
dirList.add(dir)
makeDirList={
    recurse=this
    println "added: ${it.canonicalPath}"
    dirList.add(it)
    it.eachDir {dl->makeDirList(dl);println "\ttrampoline added: ${dl.canonicalPath}"}
}

//println "List of subfolders: $dirList"
subfolderCount=0
def yokoCpImageList = new yokoCpImageList()

/* A closure to process a directory with Yoko-Metadata */
def processMeasurements={plateDir->
    println "\nProcessing images from subfolders of ${dir}"
    measurementDataFile= new File ("${plateDir}/${measurementData}")
    measurementDetailFile= new File("${plateDir}/${measurementDetail}")
    if (measurementDataFile.exists()&&measurementDetailFile.exists()){
        switch(_appendFlag){
            case false:
            yokoCpImageList.main("-o${outFile.canonicalPath}","-i${plateDir.canonicalPath}","-p$propName","$mCLI_arg")
                _appendFlag=true
                break
            case true:
            	yokoCpImageList.main("-a","-o${outFile.canonicalPath}","-i${plateDir.canonicalPath}","-p$propName","$mCLI_arg")
                break

        } //end switch

    }else{
        println "\nSub-folder <${plateDir}> contains no Yokogawa Output. Could not find MeasurementData/MeasurementDetail files! Skipping...\n"
    }

}//end each

def describeDir={
    if (it.isDirectory()) {
    println it.canonicalPath
    }else{println '-'}

}

/* if user pointed directly to a folder with images and Yoko-metadata we process just the target Yokogawa folder */
def measurementDataFile= new File ("${dir.canonicalPath}/${measurementData}")
def measurementDetailFile= new File("${dir.canonicalPath}/${measurementDetail}")
println "Checking for yoko-metadata"
if (measurementDataFile.exists()&&measurementDetailFile.exists()){
    println "Processing single folder: ${dir.canonicalPath} "
    yokoCpImageList.main("-o${outFile.canonicalPath}","-i${dir.canonicalPath}","-p$propName","$mCLI_arg",)
} //end if
    else{ //we iterate the directory subfolders
    dir.eachDirRecurse({it->processMeasurements(it)})

    }//end else

