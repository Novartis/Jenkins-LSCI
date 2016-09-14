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
 * A Groovy Utility Script for creating CellProfiler image lists from Yokogawa Data/metadata
 * Author: Ioannis K. Moutsatsos
 * Since October 3, 2013
 * last update: JULY-18-2016
 */

def cli = new CliBuilder(usage:'yokoCpImageList.groovy -i[-o -a -l -p -m]')
cli.with{
    h longOpt: 'help', 'Extracts Yokogawa Measurement Metadata'
    i longOpt: 'input', args:1, argName: 'input image source', 'Complete path to a Yokogawa image folder', required:true
    o longOpt: 'output', args:1, argName: 'output file','Output path for saving image list'
    a longOpt: 'append', args:0, argName: 'append to output file', 'Option to append or overwrite the output file'
    l longOpt: 'linuxPath', args:1, argName: 'Linux path', 'Equivalent Linux Path for input folder'
    p longOpt: 'propertiesFile',args:1,argName:'Properties file path', 'Complete path to the properties file'
    m longOpt: 'metaMap' , args:1, argName: 'wavelength map', 'Wavelength to Object-Image Map (format is CH01:OBJ1,CH02:OBJ2,CH03:OBJ3)'

}
def options = cli.parse(args)
//HARD CODED----------------------
def acquisitionPC= 'YOKOGAWA-CV7000'

//--------------------------------

def outFileDefault='./yokoImageList.csv'
def outFile= new File(outFileDefault)
def outFileWriter=null
def waveMap=[:]
def osPathSeparator= '\\'  //windows path separator
def props = new Properties() //property file for metadata
def channels=[:]   //scanner channel strings
def thumbPaths=[] //a list to hold path names

if (!options) return

if(options.h){
    cli.usage()
    return
}
def metaFolder=options.i

println "Metadata1 files in: ${metaFolder}"

if (options.o){
    outFile=new File(options.o)
}
println "Output file: ${outFile.canonicalPath}"
/* setup Properties file */
def propertiesFile = null
def propFileWriter=null
if(options.p){
    propertiesFile= new File(options.p)
} else{
    propertiesFile= new File("${outFile.parent}/plate.properties")
}

/* if appending to properties we reuse the existing ones */
if (options.a){
props.load(propertiesFile.newDataInputStream())
}

// measurement data and details require Yokogawa metadata files
def measurementData="MeasurementData.mlf"
def measurementDetail="MeasurementDetail.mrf"
def measurementDataFile= new File("$metaFolder/$measurementData")
assert measurementDataFile.exists()
def measurementDetailFile= new File("$metaFolder/$measurementDetail")
assert measurementDetailFile.exists()
//CellProfiler requires that base location is prepended to all image names
def baseImageLocation= measurementDetailFile.parent
//we use the Linux path and os path separator if user provided the -l linux path argument
if (options.l){
    osPathSeparator='/'
    baseImageLocation=options.l
}

def MeasurementDetail= new XmlSlurper().parse(measurementDetailFile).declareNamespace(bts:"http://www.yokogawa.co.jp/BTS/BTSSchema/1.0")
/* a helper class modeled from Yokogawa Measurements Details metadata */
class MeasurementDetail{
    String nameBarcode
    Integer rowCount
    Integer columnCount
    String beginTime
    Integer timePointCount
    Integer fieldCount
    Integer zCount
    Integer channelCount
    String pathName
    String pathSeparator
    String imagingComputer
    Map<String,String> userChannels
    List<Measurement> measurementsList
    List<String> measurementErrorsList
    Map<Integer,Measurement> measurementMap
    Map<Integer,Measurement> timeSeriesMap
    List<String> yokoListMetadataColumns = [
            'Metadata_Barcode',
            'Metadata_PlateSize',
            'Metadata_ImagingComputer',
            'Metadata_CreationDate',
            'Metadata_CreationTime',
            'Metadata_RowNumber',
            'Metadata_Column',
            'Metadata_FieldIndex',
            'Metadata_TimePoint',
            'Metadata_ZIndex']
    List<String> yokoListImageColumns = []

    /*
    create PathName and FileName columns from known channel metadata
    */
    def createListImageColumns(){
        userChannels.each {k,v->
            yokoListImageColumns.add("Image_PathName_$v")
            yokoListImageColumns.add("Image_FileName_$v")
        }

    }
    def getPlateSize(){
        rowCount*columnCount
    }
    /* gets a list of measurements grouped by their metadata */
    def getMeasurementGroups(){
            measurementMap=measurementsList.groupBy([
                {measurement->measurement.plateRow},
                {measurement->measurement.plateColumn},
                {measurement->measurement.fieldIndex},
                {measurement->measurement.timePoint},
                {measurement->measurement.zIndex}
            ])
    }
    /* gets a time series of measurements grouped by their metadata */
    def getMeasurementTimeSeries(){
        timeSeriesMap=measurementsList.groupBy([
                {measurement->measurement.plateRow},
                {measurement->measurement.plateColumn},
                {measurement->measurement.fieldIndex},
                {measurement->measurement.zIndex}
        ])
    }
    /*
    Get a CSV formatted row in standard CP image list format
    Each row groups images from one or more timepoint that share common metadata
     */
    def getTimeSeriesRow(wRow,wCol,field,z ){
        def measureToString={pathName +pathSeparator+ it.toString()}
        String imageSet=''
        if (timeSeriesMap==null){
            getMeasurementTimeSeries()
        }
        // check for nulls in case not all field images were acquired
        //NOTE TimeSeries Flag
        def tSeriesFlag=0 //when a time series row, otherwise >0
        if (measurementMap[wRow][wCol][field]!=null){
            imageSet= timeSeriesMap[wRow][wCol][field][z].collect(measureToString)
            return "$nameBarcode,${getPlateSize()},$imagingComputer,${beginTime.split('T')[0]},${beginTime.split('T')[1]},$wRow,$wCol,$field,$tSeriesFlag,$z,$pathName,${imageSet.replace('[','').replace(']','').replace(', ',',') }"
        }else{
            imageSet=null
            println"\tWARNING: R$wRow,C$wCol,F$field,T$timePoint,Z$z: Missing Images"
            return null
        }

    }
    /*
    Get a CSV formatted row in standard CP image list format
    Each row groups images from one or more channels that share common metadata
     */
    def getImageListRow(wRow,wCol,field,timePoint,z, errorOutputFile){
        def measureToString={it.mPath+','+it.toString()}//add PathName and FileName for each image
        def yokoMeasureCode=/^.*_T\d{4}F\d{3}L\d{2}A\d{2}Z\d{2}C(\d{2}).tif/
        String imageSet=''
        if (measurementMap==null){
            getMeasurementGroups()
        }

        if (measurementMap[wRow][wCol][field] != null && measurementMap[wRow][wCol][field][timePoint]!=null) {
            imageSet=measurementMap[wRow][wCol][field][timePoint][z].collect(measureToString).sort{a,b-> (a=~yokoMeasureCode)[0][1]<=>(b=~yokoMeasureCode)[0][1]}
            return "$nameBarcode,${getPlateSize()},$imagingComputer,${beginTime.split('T')[0]},${(beginTime.split('T')[1]).split(/\./)[0]},$wRow,$wCol,$field,$timePoint,$z,${imageSet.replace('[','').replace(']','').replace(', ',',') }"
        } else {
            imageSet=null
            if (!measurementErrorsList.contains("R${wRow}C${wCol}F${field}T${timePoint}")) {
	            println "[WARNING] Skipping image set on error list: R${wRow}C${wCol}F${field}T${timePoint}"
	            errorOutputFile << "[WARNING] Skipping image set on error list: R${wRow}C${wCol}F${field}T${timePoint}\n"
	    } else {
	            println "[WARNING] There is no timepoint ${timePoint} at measurementMap[${wRow}][${wCol}][${field}]"
	            errorOutputFile << "[WARNING] Empty or missing image set R${wRow}C${wCol}F${field}T${timePoint}\n"
	    }
            return null
        }
    }

    /* returns header row with column names */
    def getImageListHeaderRow(){
      def allColumns=yokoListMetadataColumns+yokoListImageColumns
      return allColumns.toString().replace('[','').replace(']','').replace(', ',',')
    }

}

def MeasurementData= new XmlSlurper().parse(measurementDataFile).declareNamespace(bts:"http://www.yokogawa.co.jp/BTS/BTSSchema/1.0")
class Measurement{
    String type
    String plateColumn
    String plateRow
    String timePoint
    String fieldIndex
    String zIndex
    String mChannel
    String mName
    String actionIndex
    String action
    String mPath


    def String toString() {
        return mName
    }
}
def yokoMeasureDetail=new MeasurementDetail(
        imagingComputer:acquisitionPC,
        nameBarcode: MeasurementDetail.MeasurementSamplePlate.'@bts:Name',
        rowCount: MeasurementDetail.'@bts:RowCount'.text() as Integer,
        columnCount: MeasurementDetail.'@bts:ColumnCount'.text() as Integer,
        timePointCount: MeasurementDetail.'@bts:TimePointCount'.text() as Integer,
        fieldCount: MeasurementDetail.'@bts:FieldCount'.text() as Integer,
        zCount: MeasurementDetail.'@bts:ZCount'.text() as Integer,
        beginTime: MeasurementDetail.'@bts:BeginTime',
        pathName: baseImageLocation,
        pathSeparator: osPathSeparator,
        userChannels: channels,
        measurementsList: [],
        measurementErrorsList:[]
)

println "Reading Measurement Data..."
MeasurementData.MeasurementRecord.each{
    switch(it.'@bts:Type'){
        case"IMG":
            yokoMeasureDetail.measurementsList.add(new Measurement(type: 'IMG',plateColumn: it.'@bts:Column', plateRow: it.'@bts:Row', timePoint: it.'@bts:TimePoint',fieldIndex: it.'@bts:FieldIndex',zIndex: it.'@bts:ZIndex',actionIndex: it.'@bts:ActionIndex',action: it.'@bts:Action',mChannel: it.'@bts:Ch',mPath: baseImageLocation, mName: it.text()) )
            break
        case"ERR":
            //println "\tDetected acquisition error:R${it.'@bts:Row'}C${it.'@bts:Column'}"
            tp=it.'@bts:TimePoint'==null ?0:it.'@bts:TimePoint'
            fi=it.'@bts:FieldIndex'==null ?0: it.'@bts:FieldIndex'
            mn=it.text()!=null?it.text():'NA'
            println "\t\t$mn:At TimePoint: $tp and FieldIndex: $fi"
            yokoMeasureDetail.measurementErrorsList.add("R${it.'@bts:Row'}C${it.'@bts:Column'}F${it.'@bts:FieldIndex'}T${it.'@bts:TimePoint'}")
            break
    }

}

println "Collected: ${yokoMeasureDetail.measurementsList.size()} measurements"
// a closure that creates a two dimensional array representing the well
def plateUserWells={[it.plateRow,it.plateColumn]}
def scanTimePoints ={it.timePoint}
def scanFields={it.fieldIndex}
def scanChannels={it.mChannel}
def scanZIndex={it.zIndex}

//its possible that not all wells are used. We identify which ones
def usedWells=yokoMeasureDetail.measurementsList.collect(plateUserWells).unique().sort()
//println usedWells
def theFields=yokoMeasureDetail.fieldCount
def theTimePoints=yokoMeasureDetail.timePointCount
def theZPlanes=yokoMeasureDetail.zCount
//report Plate Level Metadata
println "Barcode: ${yokoMeasureDetail.nameBarcode}"
println "Creation Time: ${yokoMeasureDetail.beginTime}"
println "PlateSize: ${yokoMeasureDetail.getPlateSize()}"
//report Well aggregate stats
println "Using: ${usedWells.size()} Wells"
println "Using: $theFields Fields/Well"
println "Using: $theTimePoints TimePoints"
println "Using: $theZPlanes Z Planes"
//Channels
yokoMeasureDetail.channelCount= yokoMeasureDetail.measurementsList.collect(scanChannels).unique().size()
println "Using: ${yokoMeasureDetail.channelCount} Channels"
/* Create Wavelength:Object Map from command line argument */
def channelRange=1..yokoMeasureDetail.channelCount
if(options.m){
    def inMeta= options.m //wavelength:object map from command line argument
    def inMetaList=inMeta.split(/,/)
    inMetaList.each{ it->
        def me=it.trim().split(/:/)
        waveMap."${me[0]}"=me[1]
    }
    // we only accept as many channel mappings as exist in the real acquisition
    channelRange.each {
        channels.put("$it",waveMap."${it.toString()}" )
    }
}else{
    //channels are simply IDed as 1,2,3 etc
    channelIds=['1','2','3','4','5','6','7','8','9','10']   //scanner channel strings

    channelRange.each {
        channels.put("$it","${channelIds[it-1]}")
    }

}
println "Verified channel mappings: $channels"
yokoMeasureDetail.userChannels=channels
yokoMeasureDetail.userChannels.each {k,v->
    thumbPaths.add("${yokoMeasureDetail.nameBarcode}_${v}_thumbnails")
    props.setProperty("object.$k",v)
}

yokoMeasureDetail.createListImageColumns()

//create lists for creating combinations. these are used as index positions later
def usedFields=yokoMeasureDetail.measurementsList.collect(scanFields).unique().sort()
def usedTimePoints=yokoMeasureDetail.measurementsList.collect(scanTimePoints).unique().sort()
def usedZPlanes=yokoMeasureDetail.measurementsList.collect(scanZIndex).unique().sort()
def wellImageStack=[usedWells,usedFields,usedTimePoints,usedZPlanes].combinations().sort()
//println wellImageStack
def wellTimeSeries=[usedWells,usedFields,usedZPlanes].combinations().sort()
if(!options.a){
    println "Writing new image list: ${outFile.canonicalPath}"
    outFileWriter = outFile.newWriter(options.a)
    outFileWriter<<yokoMeasureDetail.getImageListHeaderRow()+'\n'
}else{
    println "Appending to existing image list: ${outFile.canonicalPath}"
    outFileWriter = outFile.newWriter(options.a) //append flag is true

}
    //for each used well-field print the names of available channel images
    def errorOutputFile = new File("error_output.txt")
    wellImageStack.each{
        wellRow=it[0][0]
        wellColumn=it[0][1]
        imageField=it[1]
        imageTimePoint=it[2]
        imageZLevel=it[3]
        def imageListRow =yokoMeasureDetail.getImageListRow(wellRow,wellColumn,imageField,imageTimePoint,imageZLevel, errorOutputFile)
        if(imageListRow!=null){
        outFileWriter<< imageListRow+'\n'
        }
    }//end each

outFileWriter.flush() //flush final contents

/* Write properties file to destination folder
    Note that if we are appending to existing properties we only add new path properties
    and update the barcodes and thumbpaths  lists
 */

def thumbPathsString=thumbPaths.toString().replace(', ',',').replace('[','').replace(']','')
if (options.a){
    if(options.l){
        props.setProperty("path.linux.${yokoMeasureDetail.nameBarcode}","${yokoMeasureDetail.pathName}")
    }else{
        props.setProperty("path.${yokoMeasureDetail.nameBarcode}","${yokoMeasureDetail.pathName}")
    }

    props.setProperty('barcodes',"${props.getProperty('barcodes')},${yokoMeasureDetail.nameBarcode}")
    props.setProperty('thumbPaths',"${props.getProperty('thumbPaths')},$thumbPathsString")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.measurements","${yokoMeasureDetail.measurementsList.size()}")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.errors","${yokoMeasureDetail.measurementErrorsList.size()}")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.timepoints","$theTimePoints")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.zplanes","$theZPlanes")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.fields","$theFields")
    props.store(propertiesFile.newWriter(), null)
    println "Properties Appended to: ${propertiesFile.canonicalPath}"
}else{
    props.setProperty('imagingComputer',yokoMeasureDetail.imagingComputer)
    props.setProperty('barcodes',yokoMeasureDetail.nameBarcode)
    props.setProperty("${yokoMeasureDetail.nameBarcode}.measurements","${yokoMeasureDetail.measurementsList.size()}")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.errors","${yokoMeasureDetail.measurementErrorsList.size()}")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.timepoints","$theTimePoints")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.zplanes","$theZPlanes")
    props.setProperty("${yokoMeasureDetail.nameBarcode}.fields","$theFields")
    props.setProperty('thumbPaths',thumbPathsString)
    if(options.l){
        props.setProperty("path.linux.${yokoMeasureDetail.nameBarcode}","${yokoMeasureDetail.pathName}")
    }else{
        props.setProperty("path.${yokoMeasureDetail.nameBarcode}","${yokoMeasureDetail.pathName}")
    }
    props.setProperty('channels',yokoMeasureDetail.measurementsList.collect(scanChannels).unique().toString().replace(', ',',').replace('[','').replace(']',''))
    props.store(propertiesFile.newWriter(), null)
    println "Created Properties: ${propertiesFile.canonicalPath}"
}




