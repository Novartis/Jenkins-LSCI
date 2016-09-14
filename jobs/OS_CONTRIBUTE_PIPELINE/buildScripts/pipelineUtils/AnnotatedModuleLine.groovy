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

package pipelineUtils

/* Representation of an annotated module Line 
 * has isCase method that allows it to be used in case statement switching
*/
public class AnnotatedModuleLine
{
private String thisLine
private String lineType;
private String testFlag;
private String moduleName;
private String svnVersion, revNumber
private String cpp_Version, cpp_Revision
private String lineKey;
private String lineValue;
private minKey, maxKey, lowerKey, upperKey
private String modID;
private linePart;
private nameForType
private typeForName
private keyValue
private lineTemplate
private lineParamMap= new HashMap() //map will store test parameters and default values from annotatated pipeline

public AnnotatedModuleLine(final String newline, String newModuleId)
{

thisLine=newline
modID=newModuleId
this.linePart=newline.split(/:/)
this.lineKey=linePart[0].replace('"','').trim().replace(' ','_').replace('?','')
this.lineType='OTHER' //start with a generic type and specify as we go down the list
// pipeline revision info
    if (newline.startsWith('Version')){
        this.lineType='INFO_VERSION'
        this.cpp_Version=this.lineKey
    }
    if (newline.startsWith('SVNRevision')){
        this.lineType='INFO_SVNREVISION'
        this.cpp_Version=this.lineKey
    }
    if (newline.startsWith('DateRevision')){
        this.lineType='INFO_DATEREVISION'
        this.cpp_Version=this.lineKey
    }

// module header
if (newline.contains('module_num')){
	 this.lineType='MODULE'
	 this.moduleName=linePart[0]
	 def modulePart=newline.split(/\|/)
	 def svn=modulePart[1].split(/:/)
	 this.svnVersion=svn[1]
	 def revision=modulePart[2].split(/:/)
	 this.revNumber=revision[1]
	 modID='M'+this.linePart[2].split(/\|/)[0]
	 	}

//line with parameters participating in the test
if (!newline.contains('module_num') && newline.endsWith('YES')){
	this.getLineValue();
	this.lineType='PARAM'
	this.keyValue=lineValue.replace('"','').trim().split(/,/)
	if(this.lineKey.contains('(Min,Max)'))
		{
		this.lineType='PARAM_MINMAX'
		minKey=modID+'_'+lineKey.replace('(Min,Max)','_Min').replace(',','')
		maxKey=modID+'_'+lineKey.replace('(Min,Max)','_Max').replace(',','')
		def minMax=lineValue.replace('"','').trim().split(/,/)
		lineParamMap[minKey]=minMax[0]
		lineParamMap[maxKey]=minMax[1]
		}//end line with min max param
	if(this.lineKey.contains('Lower_and_upper'))
		{
		this.lineType='PARAM_LOWERUPPER'
		lowerKey=modID+'_'+lineKey.replace('Lower_and_upper','Lower')
		upperKey=modID+'_'+lineKey.replace('Lower_and_upper','Upper')
		def bound=lineValue.replace('"','').trim().split(/,/)
		lineParamMap[lowerKey]=bound[0]
		lineParamMap[upperKey]=bound[1]
		}//end line with low upper param
	if(this.lineType=='PARAM'){
		this.lineKey=modID+'_'+this.lineKey
		lineParamMap[lineKey]=keyValue[0]
		}//end lineType is PARAM
	}
//line with image/object/outline name definition for report output
if(newline.trim().startsWith("Name")){
	this.lineType='NAMED_TYPE'
	if(newline.endsWith('OUTPUT')){
		this.lineType='NAMED_OUTPUT'
	}	
	this.getLineValue();
	this.keyValue=lineValue.replace('"','').trim().split(/,/)
	nameForType=keyValue[0]
	if(lineKey.contains('object')){this.typeForName='OBJECT'}
	if(lineKey.contains('outline')){this.typeForName='OUTLINE'}
	if(lineKey.contains('image')){this.typeForName='IMAGE'}
	if(lineKey.endsWith('outline_image')){this.typeForName='OUTLINE_IMAGE'}
	if(lineKey.contains('of_the_file')){this.lineType='IMAGELIST'}

} //end starts with name
/*The logic order below is important!
* we first identify selects and then inputs
*/
if(newline.trim().startsWith("Select")&&(newline.contains('image')||newline.contains('illumination function')||newline.contains('object'))){
	this.lineType='SELECT'
	if(newline.endsWith('OUTPUT')){
		this.lineType='NAMED_OUTPUT'
	}	
	this.getLineValue();
    if (this.lineValue!=null){
	this.keyValue=lineValue.replace('"','').trim().split(/,/)
    }else{
        this.keyValue=''.split(/,/)
    }
	nameForType=keyValue[0]
	if(lineKey.contains('object')){this.typeForName='OBJECT'}
	if(lineKey.contains('outline')){this.typeForName='OUTLINE'}	
	if(lineKey.contains('image')){this.typeForName='IMAGE'}
	if(lineKey.endsWith('outline_image')){this.typeForName='OUTLINE_IMAGE'}	
	}
if(newline.trim().startsWith("Select the input")){
	this.lineType='INPUT'
	}			
if(newline.trim().startsWith("Input")&&(newline.contains('file location'))){
	this.lineType='IMAGEDATA_INPUT'
	this.getLineValue()
	}
if(newline.trim().startsWith("Base")&&(newline.contains('image location'))){
	this.lineType='IMAGEBASE_INPUT'
	this.getLineValue()
	}
if(newline.trim().startsWith("File selection method")){
	this.lineType='FILEMATCHER'
	}
if(newline.trim().startsWith("Text that these images have in common")){
	this.lineType='TEXTMATCHER'
	this.getLineValue()
	this.keyValue=lineValue.replace('"','').trim().split(/,/)
	}	
// finally identify if this will participate in parameter test
 if(newline.endsWith('YES')){
 	this.testFlag=true
 }else{
 	this.testFlag=false
 }

}

/**
* Method to be used by Groovy's switch implicitly when an instance of this
* class is switched on.
*
* @param compareString String passed via case to be compared to lineType
*/
public boolean isCase(final String compareString)
{
return compareString != null ? compareString.equals(this.lineType+'_'+this.testFlag) : false;
}

@Override
/* The string representation of the class is its TYPE_false/true.
 * The BOOLEAN flag represents whether this line is under test
 */
public String toString()
{
return this.lineType+'_'+this.testFlag;
}

public String getLineValue()
	{
        if (linePart.size()>1){
	        this.lineValue=linePart[1]
        } else{
            this.lineValue=null
        }
	} //end getLineKey
/*returns a parameterized template for the line */
public String getLineTemplate(){
switch(lineType)
	{
    case  ~/^INFO.*/:
    lineTemplate=linePart[0]+':'+ keyValue
    break
    case"MODULE":
	lineTemplate=linePart[0]+':[module_num:${mn}|svn_version:'+svnVersion+'|variable_revision_number:'+revNumber+'|show_window:False|notes:$pythonEscapedChars]'+'\n'	
	break
	case"PARAM_MINMAX":
	lineTemplate=linePart[0].replace('"','')+':'+'${'+minKey+'},'+'${'+maxKey+'}' +'\n'//write pair of min max paramKeys
	break
	case"PARAM_LOWERUPPER":
	lineTemplate=linePart[0].replace('"','')+':'+'${'+lowerKey+'},'+'${'+upperKey+'}'+'\n' //write pair of lower upper paramKeys
	break
	case"PARAM":
	lineTemplate=linePart[0].replace('"','')+':'+'${'+lineKey+'}'+'\n'
	break
	case"NAMED_OUTPUT":
	lineTemplate=(thisLine.replace('",','').replace('"','').replace(',OUTPUT','_${imageName}') +'\n').replace(',\n','\n')
	break
	case"INPUT":
	lineTemplate=((thisLine+'-').replace('",','').replace('"','').replace(',-','')+'_${imageName}'+'\n')
	break
	case"IMAGEDATA_INPUT":
	lineTemplate=linePart[0].replace('"','')+':'+'Elsewhere...${dx}7C$imageListPath'+'\n'
	break
	case"IMAGEBASE_INPUT":
	lineTemplate=linePart[0].replace('"','')+':'+'Elsewhere...${dx}7C$imageFolderPath'+'\n'
	break
	case"IMAGELIST":
	lineTemplate=linePart[0].replace('"','')+':'+'$imageList'+'\n'
	break	
	case"SELECT":
	lineTemplate=((thisLine+'-').replace('",','').replace('"','').replace(',-','')+'_${imageName}'+'\n')
	break
	case"TEXTMATCHER":
	//we return an InCell formatted file name
	lineTemplate=linePart[0].replace('"','')+':$wellID(fld 1 wv '+"${keyValue[0]} - ${keyValue[0]})"+'\n'
	break
	case"FILEMATCHER":
	lineTemplate=linePart[0].replace('"','')+':Text-Exact match'+'\n'
	break	
	case"NAMED_TYPE":
	lineTemplate=(thisLine+'-').replace(',-','')+'\n' //we do not append discriminator unless we are moduleTesting
	break
	case"OTHER":
	lineTemplate=(thisLine.replace('\\','\\\\').replace('$','\\$').replace('",','').replace('"','') +'\n').replace(',\n','\n')
	break
	
	}//end swicth

}//end getLineTemplate	
}	 		
