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

/*** BEGIN META {
 "name" : "trackListMetadata",
 "comment" : "Generates image list properties from metadata columns",
 "parameters" : [ 'vPathToImageList','vOrigin'],
 "core": "1.596",
 "authors" : [
 { name : "Ioannis K. Moutsatsos" }
 ]
 } END META**/


/*for use as scriptler use this header

* */

def pathToImageList=vPathToImageList
def origin =vOrigin
def cpImgList = new File(pathToImageList)
trackListMetadata(cpImgList,origin)

def trackListMetadata(imgList,origin,sep=',') {
    //imgList ; a file
    lineCount = 0
    cpMetadata = [:] // a map maintaining the values of each metadata column
    cpMetadataColumn = [] // a list of metadata columns
    primaryImages = [] //a list with names of the primary images
    imgList.eachLine {
        if (lineCount == 0) {
            cpImgListHeader = it.split(sep)
            colIndex = 0
            cpImgListHeader.each { colHeader ->
                if (colHeader.replace('"', '').startsWith('Metadata')) {
                    //we skip these columns but note their index position
                    cpMetadataColumn.add(colIndex)
                    cpMetadata.putAt(colHeader.replace('"', ''), []) //start with an empty list
                } else  if (colHeader.replace('"', '').startsWith('PathName') || colHeader.replace('"', '').startsWith('FileName')) {
                    theImage = colHeader.replace('"', '').split('_').last()
                    primaryImages.add(theImage)
                } else if (colHeader.replace('"', '').startsWith('Image_PathName') || colHeader.replace('"', '').startsWith('Image_FileName')) {
                    theImage = colHeader.replace('"', '').split('_').last()
                    primaryImages.add(theImage)
                }
                colIndex++
            }
        }
        if (lineCount > 0) {
            cpImgList = it.split(sep)
            cpImageListMeta = cpImgList[cpMetadataColumn]
            metaIndx = 0
            cpMetadata.each {
                it.value.add(cpImageListMeta[metaIndx].replace('"', ''))
                metaIndx++
            }
        }
        lineCount++
    }
    cpMetadata.each {
        it.value = it.value.unique()
    }
//    println cpMetadata
    propertiesFile = new File("$imgList.parent/imageList.properties")
    props = new Properties()
    props.put('ImageList_Origin', origin)
    props.put('ImageList_Size', (lineCount-1) as String)
    props.put('ImageList_Images', (primaryImages.unique() - ['PathName']).join(','))
    /* map values must be cast to string for valid properties*/
    cpMetadata.each { k, v ->
        props.putAt(k, (v.join(',')))
    }
    props.store(propertiesFile.newWriter(), null)


}
