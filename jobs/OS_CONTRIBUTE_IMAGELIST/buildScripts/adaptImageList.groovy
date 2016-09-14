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
 * A generic way for adapting image lists for different image source platforms
 * Supports generic path transformations as well as
 * bidirectional network shares to http adaptation
 * If multiple mappings are defined it will generate a separate adapted list
 * Mappings are provided as a linked list of maps
 * Author Ioannis K. Moutsatsos
 * Since August 11, 2016.
 */

def cli = new CliBuilder(usage:'adaptImageList.groovy -i -m', header:'adapts CP image list for various platforms\n')
cli.with{
    h longOpt: 'help', 'Show usage information'
    i longOpt: 'input', args:1, argName: 'source image list', 'input image list', required:true
    m longOpt: 'map', args:1, argName: 'path mapping', 'list of path mappings'
}
def options = cli.parse(args)

if (!options||options.h) {
    return //for exiting the script
}

def pathMap=''
def env = System.getenv()
if (options.m){
pathMap=options.m
}else{
pathMap=env['PATH_MAP']
}
def pathToImageList =options.i
def cpImgList = new File(pathToImageList)
sep = ',' //field separator
adaptImagePathsAll(cpImgList, pathMap)

/* A generic method for adapting paths*/

def adaptImagePathsAll(File cpImgList, String pathMap) {
    def outputRoot = "${cpImgList.parent}"
    def adaptedImgList = null
    def adaptType = null
    def outputWriter = null
    def adaptedListHeader = []
    def adaptedLine = null
    lineCount = 0
    def cpPathName = [:] //a map of the path names (keys) to indices (values)
    def cpUrlColumn = [] //a list of index location of the URLs, we need to remove these columns
    def cpMetadataColumn = [] //a list of index location of the Metadata columns
    def firstRow = [] //helper rows for finding nan columns
    def secondRow = []

    pathMappings = evalParameterMap(pathMap)
    firstRow = cpImgList.withReader { reader -> reader.readLine() }
    cpImgList.withReader { reader ->
        firstRow = reader.readLine().split(sep)
        secondRow = reader.readLine().split(sep)
    }
    cpImgListHeader = firstRow
    println "Original list header \n\t${cpImgListHeader.join(',')}"
    //columns with 'nan' will be excluded
    cpNanColumn = secondRow.findIndexValues { it == 'nan' }
    colIndex = 0
    cpImgListHeader.each { colHeader ->

        if (colHeader.replace('"', '').startsWith('PathName') || colHeader.replace('"', '').startsWith('FileName')) {
            adaptedListHeader[colIndex] = 'Image_' + colHeader.replace('"', '')
            cpPathName.put(colHeader.replace('"', ''), colIndex)
        } else if (colHeader.replace('"', '').startsWith('Image_PathName') || colHeader.replace('"', '').startsWith('Image_FileName')) {
            adaptedListHeader[colIndex] = colHeader.replace('"', '')
            cpPathName.put(colHeader.replace('"', ''), colIndex)
        } else if (colHeader.replace('"', '').startsWith('URL')) {
            //we skip these columns but note their index position
            cpUrlColumn.add(colIndex)
        } else if (colHeader.replace('"', '').startsWith('Metadata')) {
            //we note metadata column index position
            cpMetadataColumn.add(colIndex)
        } else {
            adaptedListHeader[colIndex] = colHeader.replace('"', '')
        }
        colIndex++
    }
    /*iterate and write a different adapted image list for each mapping*/
    pathMappings.each { pm ->
        fromPath = pm.source
        toPath = pm.destination
        switch (pm.type) {
	        case "WINDOWS-UNC":
	                lineCount = 0
	                adaptType = 'winunc'
	                adaptedImgList = new File("$outputRoot/${adaptType}_$cpImgList.name")
	                println "${pm.type}: adapted list saved to: ${adaptedImgList.canonicalPath}"
	                outputWriter = adaptedImgList.newWriter(false) //writer for adapted file list
	                cpImgList.eachLine { imagesLine ->
	
	                    if (lineCount == 0) {
	                        //write an adapted header
	                        adaptedListHeader = cpImgListHeader
	                        removeFromHeader = adaptedListHeader[cpUrlColumn + cpNanColumn]
	                        adaptedHeader = adaptedListHeader.minus(removeFromHeader).join(',') + '\n'
	                        outputWriter << adaptedHeader
	                    }
	                    if (lineCount > 0) {
	                        oriImagesLine = imagesLine.split(',')
	                        adaptedLine = imagesLine.split(',')
	                        adaptedPath = [:]
	                        cpPathName.each { k, v ->
	                            if (k.startsWith('PathName') || k.startsWith('Image_PathName') || k.startsWith('Image_FileName') || k.startsWith('FileName')) {
	                                adaptedLine[v] = oriImagesLine[v].replace(fromPath, toPath).replace('\"/\"', '/').replace('/','\\')
	                            }
	                            adaptedPath.putAt(k, adaptedLine[v].replace('"', ''))
	                        }
	                        /* remove spaces from metadata columns as they create havoc in Linux CellProfiler group -g option */
	                        cpMetadataColumn.each {
	                            adaptedLine[it] = oriImagesLine[it].replace(' ', '_')//replace each space with an underscore
	                        }
	
	                        //remove URL columns by creating a list with indices to keep
	                        adLineIndex = (0..(adaptedLine.size() - 1))
	                        keepIndex = adLineIndex.minus(cpUrlColumn + cpNanColumn)
	                        outputWriter << adaptedLine[keepIndex].join(',') + '\n'
	                    }
	                    lineCount++
	                }
	                outputWriter.flush()
	                outputWriter.close()
	                break
            case "LINUX":
                lineCount = 0
                adaptType = 'linux'
                adaptedImgList = new File("$outputRoot/${adaptType}_$cpImgList.name")
                println "${pm.type}: adapted list saved to: ${adaptedImgList.canonicalPath}"
                outputWriter = adaptedImgList.newWriter(false) //writer for adapted file list
                cpImgList.eachLine { imagesLine ->

                    if (lineCount == 0) {
                        //write an adapted header
                        adaptedListHeader = cpImgListHeader
                        removeFromHeader = adaptedListHeader[cpUrlColumn + cpNanColumn]
                        adaptedHeader = adaptedListHeader.minus(removeFromHeader).join(',') + '\n'
                        outputWriter << adaptedHeader
                    }
                    if (lineCount > 0) {
                        oriImagesLine = imagesLine.split(',')
                        adaptedLine = imagesLine.split(',')
                        adaptedPath = [:]
                        cpPathName.each { k, v ->
                            if (k.startsWith('PathName') || k.startsWith('Image_PathName') || k.startsWith('Image_FileName') || k.startsWith('URL') || k.startsWith('FileName')) {
                                adaptedLine[v] = oriImagesLine[v].replace(fromPath, toPath).replace('\"/\"', '/').replace('\\','/')
                            }
                            adaptedPath.putAt(k, adaptedLine[v].replace('"', ''))
                        }
                        /* remove spaces from metadata columns as they create havoc in Linux CellProfiler group -g option */
                        cpMetadataColumn.each {
                            adaptedLine[it] = oriImagesLine[it].replace(' ', '_')//replace each space with an underscore
                        }

                        //remove URL columns by creating a list with indices to keep
                        adLineIndex = (0..(adaptedLine.size() - 1))
                        keepIndex = adLineIndex.minus(cpUrlColumn + cpNanColumn)
                        outputWriter << adaptedLine[keepIndex].join(',') + '\n'
                    }
                    lineCount++
                }
                outputWriter.flush()
                outputWriter.close()
                break
            case "HTTP-SERVER":
                lineCount = 0
                adaptType = 'web'
                hasURL = false //default option gets reset if list has URL options
                adaptedImgList = new File("$outputRoot/${adaptType}_$cpImgList.name")
                println "${pm.type}: adapted list saved to: ${adaptedImgList.canonicalPath}"
                outputWriter = adaptedImgList.newWriter(false) //writer for adapted file list
                cpImgList.eachLine { imagesLine ->

                    if (lineCount == 0) {
                        //write an adapted header-removing nan columns
                        adaptedListHeader = cpImgListHeader
                        if (cpImgListHeader.findIndexValues { it.contains('URL') }.size() == 0) {
                            println 'No URL Columns-Adding new'
                            cpUrlColumn = cpImgListHeader.findAll { it.contains('PathName') }.collect {
                                it.replace('PathName', 'URL')
                            }
                            adaptedListHeader = cpImgListHeader.plus(cpUrlColumn)

                        } else {
                            println 'URL Columns Exist-Updating them'
                            hasURL = true
                        }
                        removeFromHeader = adaptedListHeader[cpNanColumn]
                        adaptedHeader = adaptedListHeader.minus(removeFromHeader).join(',') + '\n'
                        println "Adapted list header \n\t$adaptedHeader"
                        outputWriter << adaptedHeader
                    }
                    if (lineCount > 0) {
                        oriImagesLine = imagesLine.split(',')
                        adaptedLine = imagesLine.split(',')
                        adaptedPath = [:]
                        /*if URL columns exist we replace them with destination http*/
                        if (hasURL) {
                            cpImgListHeader.findAll { it.contains('URL') }.each {
                                urlIndex = cpImgListHeader.findIndexOf { url -> url == it }
                                adaptedLine[urlIndex] = oriImagesLine[urlIndex].replace('file://', '').replace(fromPath.replace('\\', '/'), toPath).replace('\"/\"', '/')
                            }
                        }
                        /* If list has no original URLs we create them */
                        if (!hasURL) {
                            adaptedListHeader.findAll { it.contains('URL') }.each {
                                urlIndex = adaptedListHeader.findLastIndexOf { url -> it }
                                concept = it.split('_').last()
                                pathIndex = (cpPathName.find { it.key.endsWith("PathName_$concept" as String) }).value
                                fileIndex = (cpPathName.find { it.key.endsWith("FileName_$concept" as String) }).value
                                httpPrefix = oriImagesLine[pathIndex].replace(fromPath, toPath).replace('\"/\"', '/')
                                adaptedLine = adaptedLine + ["$httpPrefix/${adaptedLine[fileIndex]}".replace('\"/\"', '/').replace(' ', '%20')]
                            }
                        }
                        /*now adapt PathNames */
                        cpImgListHeader.findAll { it.contains('PathName') }.each {
                            urlIndex = cpImgListHeader.findIndexOf { url -> url == it }
                            adaptedLine[urlIndex] = oriImagesLine[urlIndex].replace('file://', '').replace(fromPath, toPath).replace('\"/\"', '/')
                        }
                        /* remove spaces from metadata columns as they create havoc in Linux CellProfiler group -g option */
                        cpMetadataColumn.each {
                            adaptedLine[it] = oriImagesLine[it].replace(' ', '_')//replace each space with an underscore
                        }
                        //remove URL columns by creating a list with indices to keep
                        adLineIndex = (0..(adaptedLine.size() - 1))
                        keepIndex = adLineIndex.minus(cpNanColumn)
                        outputWriter << adaptedLine[keepIndex].join(',') + '\n'
                    }
                    lineCount++
                }
                outputWriter.flush()
                outputWriter.close()
                break
            default:
                println pm.type //do default actions
        }

    }


}

/* a method to evaluate a Jenkins generated parameter formatted as a list of map entries

  */

def evalParameterMap(param) {
    mapTemplate = '[=]'
    mapString = mapTemplate.replace('=', param.replace('][', '],[').replace('\\', '\\\\'))
    map = evaluate(mapString)
}
