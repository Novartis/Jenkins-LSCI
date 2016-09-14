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

/* A Jenkins Script to recursively iterate InCell output folders to parse the xdce acquisition metadata
 * The recursive parser generates a CellProfiler style, csv formatted image list
 * No attempt is made to remap paths. So its used for mapping directly to windows Paths
 * The image list contains important acquisition metadata directly extracted from the xdce file
 * Author Ioannis K. Moutsatsos
 * Since November 12, 2012
 * Last Update July 22, 2016
 */
 
def cli = new CliBuilder(usage:'xdceSlurperMapRecurse.groovy -i -m', header: 'Creates a CellProfiler image list from one or more InCell xdce files\n')
 cli.with{
     h longOpt: 'help', 'usage information'
     i longOpt: 'source', args:1, argName: 'source_folder', 'InCell folder or parent InCell folder(s)', required:true
     m longOpt: 'wavemap', args:1, argName: 'wave_object_map', 'wavelength-object map', required:true
     d longOpt: 'destination', args:1, argName: 'destination', 'image list destination'
     p longOpt: 'postfix', args:1, argName: 'postfix', 'image list name postfix'
     }

def options = cli.parse(args)
 if (!options||options.h) {
        return
        }

commonPrefix="imageList_${options.p}"

def commonPrefix='imageList'           
if (options.p){
commonPrefix="imageList_${options.p}"
} 

def _outputFolder=''          
if (!options.d){
 _outputFolder=options.i
}else{
 _outputFolder=options.d
 }
//assert options.i!=null,"ERROR: Must provide complete path to source InCell folder "
//assert options.m!=null, "ERROR: Must provide a Wavelength:Object map "
//

def _targetFolder = options.i
def _csvheader = false //no csv file header has been written


generatedImgList = _outputFolder + "/" + "${commonPrefix}.csv"
def outFile = new File(generatedImgList)

def dir = new File(_targetFolder)
assert dir.exists()
def waveMap = [:]
/* Create Wavelength:Object Map from command line argument */
if (options.m) {
    def inMeta = options.m //wavelength:object map from command line argument
    def inMetaList = inMeta.split(/,/)
    inMetaList.each { it ->
        def me = it.trim().split(/:/)
        waveMap."${me[0]}" = me[1]
    }
}

/* If target exists we iterate the sub-folders */

subfolderCount = 0
println "\nProcessing images from subfolders of ${dir}"
//-------------------
dir.eachDir { plateDir ->
    def xdceFileName = plateDir.name + '.xdce'
    def xdceFile = new File("${plateDir}/${xdceFileName}")
    if (xdceFile.exists()) {
// println "$plateDir with XDCE"
        switch (_csvheader) {
            case false:
//     println "No header-making it"
                genImageList(plateDir, outFile, true, waveMap)
                _csvheader = true
                break
            case true:
//     println "Skip header"
                genImageList(plateDir, outFile, false, waveMap)
                break
        } //end switch

    } else {
        println "\nSub-folder <${plateDir}> contains no InCell Output. Could not find xdce file! Skipping...\n"
    }

}//end each

/* if user pointed directly to a folder with images and xdce (has no subfolder) */
def _targetXdceFileName = dir.name + '.xdce'
def _targetXdceFile = new File("${_targetFolder}/${_targetXdceFileName}")
if (_targetXdceFile.exists()) {
    genImageList(dir, outFile, true, waveMap)
    _csvheader = true
}

/* print notification depending on whether a csv file header has been written */
if (_csvheader) {
    println 'Created Image List: ' + outFile.canonicalPath
} else {
    println 'Could not find any InCell Metadata files (xdce). Check that folders were generated from an InCell scanner'
}

//-----------------------------------------------------------------
/* Note that if multiple CSV files will be merged 
 * the headerFlag must be false after the first one 
 */

def genImageList(input, imageList, headerFlag, waveMap) { //start closure
    def header = headerFlag
    def xdceFolder = input
        assert xdceFolder.exists()
    def outFile=imageList.newWriter(true)
    def xdceFileName = xdceFolder.name + '.xdce'
    def xdceFile = new File("${input}/${xdceFileName}")
    def wvImageMap = new HashMap()
    def metadataMap = new HashMap()
    def ImageStack = new XmlSlurper().parse(xdceFile)
// println 'Done Parsing: ' +xdceFile.canonicalPath

// println 'Detecting wavelength channels:'
    excitationFilters = []
    if (headerFlag) {
        ImageStack.AutoLeadAcquisitionProtocol.Wavelengths.Wavelength.ExcitationFilter.each {
            excitationFilters.add(it.@name)
        }
    }//do only for first file
    ImageStack.AutoLeadAcquisitionProtocol.Wavelengths.Wavelength.ExcitationFilter.each {
        /* confirm that wavelength is in user's wave map */
        wv = "${it.@name}"
        if (waveMap.containsKey(wv.toString())) {
            wvImageMap."${wv}_FileName" = "Image_FileName_${waveMap."$wv"}"
            wvImageMap."${wv}_PathName" = "Image_PathName_${waveMap."$wv"}"
            println "\tMapped $wv to ${waveMap."$wv"}"
        } else {
            println "$wv NOT FOUND in supplied user map containing: ${waveMap.keySet()}"
            println "ERROR: Include mappings for ALL Excitation Filters:$excitationFilters\n"
            throw new RuntimeException()
        }
    }
    /*
    Patch for DMPQM-176
    Each ExcitationFilter will be split on a dash and only the second part will be used
    Requirement due to 'TL-Brightfield' ExcitationFilter being assigned to 'Brightfield' when images are iterated
    Note that this may not be a complete fix if GE decides to use yet a different scheme!
     */
    ImageStack.AutoLeadAcquisitionProtocol.Wavelengths.Wavelength.ExcitationFilter.each {
        wv = "${it.@name}"
        if (wv.contains('-')) {
            wvImageMap."${wv.split('-')[1]}_FileName" = "Image_FileName_${waveMap."$wv"}"
            wvImageMap."${wv.split('-')[1]}_PathName" = "Image_PathName_${waveMap."$wv"}"
            wvImageMap.remove(wv.toString())
            println "\tRemapping Excitation Wavelength: $wv : ${wv.split('-')[1]}"
        }
    }

    if (header) {
        thisCsvRow = 'Metadata_Barcode,Metadata_PlateSize,Metadata_ImagingComputer,Metadata_CreationDate,Metadata_CreationTime,Metadata_RowNumber,Metadata_Column,Metadata_FieldIndex,Metadata_TimeIndex,Metadata_ZIndex'
    } else {
        thisCsvRow = ''
    }
    println "\tPlease, wait for: ${xdceFile.name}-> ${wvImageMap.size()}:Channels-> ${ImageStack.Images.Image.size()}: Images"

    thisRow = null
    thisColumn = null

    metadataMap.'barcode' = extractBarcode(xdceFolder.name)//ImageStack.UUID.@value
    metadataMap.'imagingComputer' = ImageStack.Computer.@name
    metadataMap.'creationDate' = ImageStack.Creation.@date
    metadataMap.'creationTime' = ImageStack.Creation.@time
    numColumns = ImageStack.AutoLeadAcquisitionProtocol.Plate.@columns.text().toInteger()
    numRows = ImageStack.AutoLeadAcquisitionProtocol.Plate.@rows.text().toInteger()
    metadataMap.'plateSize' = (numColumns * numRows)

    imageStackCount = 0
    ImageStack.Images.Image.each {
        if (it.Well.Row.@number != thisRow || it.Well.Column.@number != thisColumn || it.Identifier.@field_index != thisField || it.Identifier.@time_index != thisTime || it.Identifier.@z_index != thisZ) {
            if (thisCsvRow != '') {
                wvImageMap.sort().each { key, value -> thisCsvRow = thisCsvRow + ",${value}" }
            }
            outFile << "${thisCsvRow}\n"
            //reset row CONTENT     to just the METADATA
            thisCsvRow = "${metadataMap.barcode},${metadataMap.plateSize},${metadataMap.imagingComputer},${metadataMap.creationDate},${metadataMap.creationTime},"
            //add new content
            thisCsvRow = thisCsvRow + "${it.Well.Row.@number},${it.Well.Column.@number},${it.Identifier.@field_index},${it.Identifier.@time_index},${it.Identifier.@z_index}"
            thisRow = it.Well.Row.@number
            thisColumn = it.Well.Column.@number
            thisField = it.Identifier.@field_index
            thisTime = it.Identifier.@time_index
            thisZ = it.Identifier.@z_index
            wvImageMap."${it.ExcitationFilter.@name}_PathName" = "${input}"
            wvImageMap."${it.ExcitationFilter.@name}_FileName" = it.@filename
        } else {
            wvImageMap."${it.ExcitationFilter.@name}_PathName" = "${input}"
            wvImageMap."${it.ExcitationFilter.@name}_FileName" = it.@filename
        }

    }

//finally flush the last row construct
    wvImageMap.sort().each { key, value -> thisCsvRow = thisCsvRow + ",${value}" }
    outFile << "${thisCsvRow}"
    outFile.flush()
    outFile.close()
} //end closure    

/*parses a plate barcode from a folder name
* if barcode is not found the folder name is used as barcode, similarly to HCS Explorer
* */

def extractBarcode(folderName) {
    nameParts = []
    barcode = ''
    def bcRegEx = /^.*_([A-Z]{2}\d{8})_.*/
    def bcMatcher = (folderName =~ bcRegEx)
    if (bcMatcher.matches()) {
        barcode = bcMatcher[0][1]
        println "\tBarcode: $barcode"
    } else {
        println '\tNo barcode present: Using folder name'
        nameParts = folderName.split('\\\\')
        barcode = nameParts[nameParts.length-1 ].replace(' ','_')
        println "\tPseudo-Barcode: $barcode"

    }
    return barcode
}