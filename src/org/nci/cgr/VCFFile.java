package org.nci.cgr;

import java.io.BufferedWriter;
import java.io.File;

import java.io.IOException;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;

import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.*;

public class VCFFile {
	private String filePath;
	private String caller;
	private File vcfFile;
	protected Byte set;
    protected int priority;
	private static VCFFileReader vcfFileReader;
	private VCFHeader mHeader = null;
	public static String TUMOR_TAG1="TUMOR";
	public static String TUMOR_TAG2="TUMOUR";
	public static final String NORMAL_TAG="NORMAL";
	public static final String QUAL_TAG="QUAL";
	private static final String VERSION_LINE =
            VCFHeader.METADATA_INDICATOR + VCFHeaderVersion.VCF4_2.getFormatString() + "=" + VCFHeaderVersion.VCF4_2.getVersionString();
	public VCFFile(String filePath, String caller,int priority, Byte set) {
		super();
		this.filePath = filePath;
		this.caller = caller;
		this.set=set;
		this.priority=priority;
	}
	
	
	public VCFHeader getmHeader() {
		return mHeader;
	}


	public void setmHeader(VCFHeader mHeader) {
		this.mHeader = mHeader;
	}


	public void writeVariants(BufferedWriter bw,Variant vv, int snvCallerNum,int indelCallerNum) throws IOException, ClassNotFoundException {
//		if (vv.getVariantContext().getStart()==58042518)
//			System.out.println("found!");
		
		Variant variant=new Variant(vv.getVariantContext(),vv.getCaller(),vv.getPriority(),vv.getSet());
		// variant.g
		bw.append(vv.getVariantContext().getContig()+"\t"+vv.getVariantContext().getStart()+"\t"+vv.getVariantContext().getID()+"\t"+
	              vv.getVariantContext().getAlleles().get(0).getBaseString()+"\t");
		
		if (vv.getVariantContext().getAlleles().size()>1)
			bw.append(vv.getVariantContext().getAlleles().get(1).getBaseString()+"\t");
		else
			bw.append(vv.getVariantContext().getAlleles().get(0).getBaseString()+"\t");
		
		Map<String,Object> info=vv.getVariantContext().getAttributes();
		String infoContent="";
		String format="";
		String gtString="";
		//vcfEncoder.write(bw, vv.getVariantContext());
		if (vv.getVariantContext().hasGenotypes()) {
		   Set<String> sampleNames=vv.getVariantContext().getSampleNames();
		   if ((sampleNames.size()!=2) && (sampleNames.size()!=0)) {
			   SomaticCombiner.logger.log(Level.WARNING,"Error: the samples in "+filePath+" not equals to 2 or 0! The variants in this file will be skipped!");
               return;
		   }
		   if (sampleNames.size()==2) {
			   String[] samples=new String[2];
			   boolean foundTumor=false;
			   for(String sampleName:sampleNames) 
				   if (sampleName.toUpperCase().contains(TUMOR_TAG1)||sampleName.toUpperCase().contains(TUMOR_TAG2)) {
					  samples[0]=sampleName;
					  foundTumor=true;  
				   }
				   else
					  samples[1]=sampleName;
			   if (!foundTumor) {
				   SomaticCombiner.logger.log(Level.WARNING,"Error: the samples in "+filePath+" does not contain TUMOR in the genotype columns! The variants in this file will be skipped!");
	               return; 
			   }
			   Set<String> extendedGtKeyset=new TreeSet<String>();
			   for(int i=0;i<samples.length;i++) {
				   Genotype gt=vv.getVariantContext().getGenotype(samples[i]);
				   Map<String,Object> extendedAttributes=gt.getExtendedAttributes();
				   for (String key:extendedAttributes.keySet()) {
					   extendedGtKeyset.add(key);
				   }
			   }
			   for(int i=0;i<samples.length;i++) {
				   Genotype gt=vv.getVariantContext().getGenotype(samples[i]);

				   if (i==0) 
					   if (gt.getAlleles().size()>0) format="GT";
				   if (!format.contains("GT")){
				   		if ((i>0) && !gt.isCalled())
				   		    gtString+="0/0"+VCFConstants.GENOTYPE_FIELD_SEPARATOR;
				   }
				  
				   if (gt.isCalled()) {
					   if (gt.isHom()){							
							if (gt.isHomRef())
								gtString+="0/0";  // 0/0
							if (gt.isHomVar())
								gtString+="1/1";  // 1/1
						}
						if (gt.isHet()){
							if (gt.isHetNonRef())
								gtString+="1/2";  // 1/2 e.g.
							else
								gtString+="0/1";;  // 0/1
						}						
					}
						   
		
				   if (gt.hasDP()) {
					   if (i==0) {
						   if (format.length()==0)
							   format="DP";
						   else
							   format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+"DP";
					   }
					   if (gt.getAlleles().size()==0)
						   gtString=gtString+gt.getDP();
					   else
				           gtString=gtString+":"+gt.getDP();
				   }
			
				   if (gt.hasAD()) {  
					   gtString=gtString+":"+Arrays.toString(gt.getAD()).replace("[", "").replace("]", "").replace(" ","").trim();
					   if (i==0) format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+"AD";   
				   }
				   Map<String,Object> extendedAttributes=gt.getExtendedAttributes() ;
				   for (String key:extendedGtKeyset) {
					   if (i==0) {
						   if (vv.getCaller().contains(","))
					          format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+key;
						   else
							  format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+SomaticCombiner.callerName(vv.getCaller())+"_"+key;
					   }
					   if (extendedAttributes.containsKey(key))
					     gtString=gtString+VCFConstants.GENOTYPE_FIELD_SEPARATOR+extendedAttributes.get(key).toString().trim();
					   else
						 gtString=gtString+VCFConstants.GENOTYPE_FIELD_SEPARATOR+".";
				   }
				   
				   gtString=gtString+"\t";
				   if (!format.contains("GT")){
				   		if (i==0) 
				   		    gtString="0/1"+VCFConstants.GENOTYPE_FIELD_SEPARATOR+gtString;
				   }
				   
			   }   
			}
		}
		else {
			format="GT";
			gtString="0/1\t0/0";
		}
		if (!format.contains("GT"))
			format="GT"+VCFConstants.GENOTYPE_FIELD_SEPARATOR+format;
		// List<String> keysSorted = info.keySet().stream().collect(Collectors.toList());
		List<String> aList = info.keySet().stream().collect(Collectors.toList());
		aList.sort(Comparator.naturalOrder());
		for (String key:aList) {		
			if (Integer.bitCount(vv.getSet())==1)
				infoContent=infoContent+SomaticCombiner.callerName(vv.getCaller())+"_"+key+"="+info.get(key).toString().replace("[", "").replace("]", "").replace(" ","").trim()+VCFConstants.INFO_FIELD_SEPARATOR;
			else
		        infoContent=infoContent+key+"="+info.get(key).toString().replace("[", "").replace("]", "").replace(" ","").trim()+VCFConstants.INFO_FIELD_SEPARATOR;			
		}
		bw.append(".\t");
		String WESPass="ADJ_LowConf";
		float tumorAF=0;
		int tumorDP=0;
		if (vv.getVariantContext().isSNP()) {
			tumorAF=vv.getTumorAF();
			tumorDP=vv.getTumorDP();
			
			// deep sequencing
			// if (vv.getCaller().contains("strelka") && vv.getCaller().contains("lofreq") || vv.getCaller().contains("strelka") && vv.getCaller().contains("vardict")
			//		  || vv.getCaller().contains("vardict") && vv.getCaller().contains("lofreq")) 
			if ((float)Integer.bitCount(vv.getSet()) / snvCallerNum >= 0.5) 
				WESPass="ADJ_PASS";
		    if (tumorAF<0.03 && tumorAF>0 && tumorDP>10 && vv.getCaller().contains("mutect2") )
		    // if (tumorAF<0.03 && tumorAF>0 && tumorDP>10 && Integer.bitCount(vv.getSet())>=1 )
				WESPass="ADJ_PASS";
		    if (tumorAF<=0.1 && tumorAF>=0.03 && tumorDP>10 && vv.getCaller().contains("mutect2") && vv.getCaller().contains("strelka"))
//			// if (tumorAF<=0.1 && tumorAF>=0.03 && tumorDP>10 && Integer.bitCount(vv.getSet())>1)	
				WESPass="ADJ_PASS";

		}
		else {
			tumorAF=vv.getTumorAF();
			tumorDP=vv.getTumorDP();
			if ((float)Integer.bitCount(vv.getSet()) / indelCallerNum >= 0.5)
				WESPass="ADJ_PASS";

		}
		
		if (vv.getVariantContext().isSNP()) {
			if ((float)Integer.bitCount(vv.getSet()) / snvCallerNum >= 0.5)
				bw.append("PASS" + ";"+WESPass+"\t");
			else
				bw.append("LowConf" + ";"+WESPass+"\t");
		} else {
			if ((float)Integer.bitCount(vv.getSet()) / indelCallerNum >= 0.5)
				bw.append("PASS"  + ";"+WESPass+"\t");
			else
				bw.append("LowConf"  + ";"+WESPass+ "\t");
		}
						
		bw.append(infoContent);
		// if (vv.getVariantContext().isSNP()) {
		if (tumorAF!=-1)
		    bw.append("Tumor_AF="+tumorAF+";");
		if (tumorDP>0)
			bw.append("Tumor_DP="+tumorDP+";");
			
		// }
		bw.append(SomaticCombiner.COUNT_TAG+"="+Integer.bitCount(vv.getSet())+VCFConstants.INFO_FIELD_SEPARATOR+SomaticCombiner.callerSymbols+"="+voting(vv));
		
		bw.append("\t"+format);
		if (gtString.endsWith("\t")) {
			gtString = gtString.substring(0, gtString.length()-1);
		}
		bw.append("\t"+gtString);
		
		
		bw.newLine();
		bw.flush();
	}
	
	
	public float segmentVariants(Variant vv, int snvCallerNum,int indelCallerNum) throws IOException, ClassNotFoundException {
//		if (vv.getVariantContext().getStart()==6263642)
//			System.out.println("found!");
		
		Variant variant=new Variant(vv.getVariantContext(),vv.getCaller(),vv.getPriority(),vv.getSet());

		
		Map<String,Object> info=vv.getVariantContext().getAttributes();
		String infoContent="";
		String format="";
		String gtString="";
		//vcfEncoder.write(bw, vv.getVariantContext());
		if (vv.getVariantContext().hasGenotypes()) {
		   Set<String> sampleNames=vv.getVariantContext().getSampleNames();
		   if ((sampleNames.size()!=2) && (sampleNames.size()!=0)) {
			   SomaticCombiner.logger.log(Level.WARNING,"Error: the samples in "+filePath+" not equals to 2 or 0! The variants in this file will be skipped!");
               return -1;
		   }
		   if (sampleNames.size()==2) {
			   String[] samples=new String[2];
			   boolean foundTumor=false;
			   for(String sampleName:sampleNames) 
				   if (sampleName.toUpperCase().contains(TUMOR_TAG1)||sampleName.toUpperCase().contains(TUMOR_TAG2)) {
					  samples[0]=sampleName;
					  foundTumor=true;  
				   }
				   else
					  samples[1]=sampleName;
			   if (!foundTumor) {
				   SomaticCombiner.logger.log(Level.WARNING,"Error: the samples in "+filePath+" does not contain TUMOR in the genotype columns! The variants in this file will be skipped!");
	               return -1; 
			   }
			   Set<String> extendedGtKeyset=new TreeSet<String>();
			   for(int i=0;i<samples.length;i++) {
				   Genotype gt=vv.getVariantContext().getGenotype(samples[i]);
				   Map<String,Object> extendedAttributes=gt.getExtendedAttributes();
				   for (String key:extendedAttributes.keySet()) {
					   extendedGtKeyset.add(key);
				   }
			   }
			   for(int i=0;i<samples.length;i++) {
				   Genotype gt=vv.getVariantContext().getGenotype(samples[i]);

				   if (i==0) 
					   if (gt.getAlleles().size()>0) format="GT";
				   
				  
				   if (gt.isCalled()) {
					   if (gt.isHom()){							
							if (gt.isHomRef())
								gtString+="0/0";  // 0/0
							if (gt.isHomVar())
								gtString+="1/1";  // 1/1
						}
						if (gt.isHet()){
							if (gt.isHetNonRef())
								gtString+="1/2";  // 1/2 e.g.
							else
								gtString+="0/1";;  // 0/1
						}						
					}
						   
		
				   if (gt.hasDP()) {
					   if (i==0) {
						   if (format.length()==0)
							   format="DP";
						   else
							   format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+"DP";
					   }
					   if (gt.getAlleles().size()==0)
						   gtString=gtString+gt.getDP();
					   else
				           gtString=gtString+":"+gt.getDP();
				   }
			
				   if (gt.hasAD()) {  
					   gtString=gtString+":"+Arrays.toString(gt.getAD()).replace("[", "").replace("]", "").replace(" ","").trim();
					   if (i==0) format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+"AD";   
				   }
				   Map<String,Object> extendedAttributes=gt.getExtendedAttributes() ;
				   for (String key:extendedGtKeyset) {
					   if (i==0) {
						   if (vv.getCaller().contains(","))
					          format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+key;
						   else
							  format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+SomaticCombiner.callerName(vv.getCaller())+"_"+key;
					   }
					   if (extendedAttributes.containsKey(key))
					      gtString=gtString+VCFConstants.GENOTYPE_FIELD_SEPARATOR+extendedAttributes.get(key).toString();
					   else
						   gtString=gtString+VCFConstants.GENOTYPE_FIELD_SEPARATOR+".";
				   }
				   
				   gtString=gtString+"\t";
			   }   
			}
		}
		else {
			format="GT";
			gtString="0/1\t0/0";
		}
		// List<String> keysSorted = info.keySet().stream().collect(Collectors.toList());
		List<String> aList = info.keySet().stream().collect(Collectors.toList());
		aList.sort(Comparator.naturalOrder());
		for (String key:aList) {		
			if (Integer.bitCount(vv.getSet())==1)
				infoContent=infoContent+SomaticCombiner.callerName(vv.getCaller())+"_"+key+"="+info.get(key).toString().replace("[", "").replace("]", "").replace(" ","").trim()+VCFConstants.INFO_FIELD_SEPARATOR;
			else
		        infoContent=infoContent+key+"="+info.get(key).toString().replace("[", "").replace("]", "").replace(" ","").trim()+VCFConstants.INFO_FIELD_SEPARATOR;			
		}
//		bw.append(".\t");
		String WESPass="ADJ_LowConf";
		float tumorAF=0;
		int tumorDP=0;
		if (vv.getVariantContext().isSNP()) {
			tumorAF=vv.getTumorAF();
			tumorDP=vv.getTumorDP();
			
			// deep sequencing
			// if (vv.getCaller().contains("strelka") && vv.getCaller().contains("lofreq") || vv.getCaller().contains("strelka") && vv.getCaller().contains("vardict")
			//		  || vv.getCaller().contains("vardict") && vv.getCaller().contains("lofreq")) 
			if ((float)Integer.bitCount(vv.getSet()) / snvCallerNum >= 0.5) 
				return tumorAF;

		}

		return 2;

	}
	private static void rejectVCFV43Headers(final VCFHeader targetHeader) {
        if (targetHeader.getVCFHeaderVersion() != null && targetHeader.getVCFHeaderVersion().isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            throw new IllegalArgumentException(String.format("Writing VCF version %s is not implemented", targetHeader.getVCFHeaderVersion()));
        }

    }
	
	public void mergeHeader(VCFHeader header) {
		for (final VCFHeaderLine line : header.getMetaDataInSortedOrder()) {
			if (VCFHeaderVersion.isFormatString(line.getKey()))
				continue;
            
			mHeader.addMetaDataLine(line);
		}
		
	}
		
	public static VCFHeader writeHeader(VCFHeader header, final Writer writer) {

		try {
			rejectVCFV43Headers(header);
			String versionLine=VERSION_LINE;

			// the file format field needs to be written first
			writer.write(versionLine + "\n");

			for (final VCFHeaderLine line : header.getMetaDataInSortedOrder()) {
				if (VCFHeaderVersion.isFormatString(line.getKey()))
					continue;

				writer.write(VCFHeader.METADATA_INDICATOR);
				writer.write(line.toString());
				writer.write("\n");
			}

			// write out the column line
			writer.write(VCFHeader.HEADER_INDICATOR);
			boolean isFirst = true;
			for (final VCFHeader.HEADER_FIELDS field : header.getHeaderFields()) {
				if (isFirst)
					isFirst = false; // don't write out a field separator
				else
					writer.write(VCFConstants.FIELD_SEPARATOR);
				writer.write(field.toString());
			}

			if (header.hasGenotypingData()) {
				writer.write(VCFConstants.FIELD_SEPARATOR);
				writer.write("FORMAT");
				for (final String sample : header.getGenotypeSamples()) {
					writer.write(VCFConstants.FIELD_SEPARATOR);
					writer.write(sample);
				}
			}
			else {
				writer.write(VCFConstants.FIELD_SEPARATOR+"FORMAT"+VCFConstants.FIELD_SEPARATOR+"TUMOR"+VCFConstants.FIELD_SEPARATOR+"NORMAL");
				
			}

			writer.write("\n");
			writer.flush(); // necessary so that writing to an output stream will work
		} catch (IOException e) {
			throw new RuntimeIOException("IOException writing the VCF header to " , e);
		}

		return header;
	}
	

	private VCFHeader processHeader() {
		// List<VCFFilterHeaderLine> vcfFilterLines=mHeader.getFilterLines();
		// Collection<VCFFormatHeaderLine> vcfFormatHeaderLines=mHeader.getFormatHeaderLines();
		Set<VCFHeaderLine> vcfHeaderLines=mHeader.getMetaDataInInputOrder();
		Set<VCFHeaderLine> newHeader=new HashSet<VCFHeaderLine>();
		SomaticCombiner.logger.log(Level.FINER,"VCF Header size:"+vcfHeaderLines.size());
		for (VCFHeaderLine vcfHeaderLine:vcfHeaderLines) {
			
			SomaticCombiner.logger.log(Level.FINER,"Line:"+vcfHeaderLine);
//			vcfHeaderLine.getValue()
			SomaticCombiner.logger.log(Level.FINER,vcfHeaderLine.getKey());
			SomaticCombiner.logger.log(Level.FINER,vcfHeaderLine.getValue());
			// vcfHeaderLine.toStringEncoding(MapM)
			// vcfHeaderLine.toStringEncoding(arg0)
		
			if (!vcfHeaderLine.getKey().contains("FILTER") && !vcfHeaderLine.getKey().contains("INFO")&& !vcfHeaderLine.getKey().contains("FORMAT")) 
				
					newHeader.add(vcfHeaderLine);
				
			
			
		}
//		return new VCFHeader(newHeader);
//		Collection<VCFHeaderLine> vcfOtherHeaderlines=mHeader.getOtherHeaderLines();
//		List<VCFIDHeaderLine> vcfIDLines=mHeader.getIDHeaderLines();
        Collection<VCFInfoHeaderLine> vcfInfoHeaderLines=mHeader.getInfoHeaderLines();
        for (VCFInfoHeaderLine vcfInfoHeaderLine: vcfInfoHeaderLines) {
        	SomaticCombiner.logger.log(Level.FINER,vcfInfoHeaderLine.getID()+":"+vcfInfoHeaderLine);
        
       
        	VCFInfoHeaderLine newVCFInfoHeaderLine=new VCFInfoHeaderLine(vcfInfoHeaderLine.toString().replace("ID=","ID="+SomaticCombiner.callerName(this.caller)+"_"),VCFHeaderVersion.VCF4_2);
        	// VCFInfoHeaderLine newVCFInfoHeaderLine=new VCFInfoHeaderLine(SomaticCombiner.callerName(this.caller)+"_"+vcfInfoHeaderLine.getID(),vcfInfoHeaderLine.getCount(),vcfInfoHeaderLine.getType(),vcfInfoHeaderLine.getDescription());
        	newHeader.add(newVCFInfoHeaderLine);
        }
        Collection<VCFFormatHeaderLine> vcfFormatHeaderLines=mHeader.getFormatHeaderLines();
        for (VCFFormatHeaderLine vcfFormatHeaderLine: vcfFormatHeaderLines) {
        	// SomaticCombiner.logger.log(Level.INFO,vcfFormatHeaderLine.getID());
        	if (vcfFormatHeaderLine.getID()=="GT" ||vcfFormatHeaderLine.getID()=="AD" || vcfFormatHeaderLine.getID()=="DP")
        		newHeader.add(vcfFormatHeaderLine);
        	else {
	        	VCFFormatHeaderLine newVCFFormatHeaderLine=new VCFFormatHeaderLine(vcfFormatHeaderLine.toString().replace("ID=","ID="+SomaticCombiner.callerName(this.caller)+"_"),VCFHeaderVersion.VCF4_2);
	        	newHeader.add(newVCFFormatHeaderLine);
        	}
        }
        
        return new VCFHeader(newHeader);
	}
	
	
	public int importVariants(List<Variant> list) throws ClassNotFoundException, SQLException {
		vcfFile = new File(filePath);

		vcfFileReader = new VCFFileReader(vcfFile, false);
		
		mHeader = vcfFileReader.getFileHeader();
		
		ArrayList<String> sampleNames=mHeader.getSampleNamesInOrder();
		boolean foundTumor=false;
		if (sampleNames.size()==2) {
			for(String s:sampleNames) {
				if (s.toUpperCase().contains(TUMOR_TAG1) || s.toUpperCase().contains(TUMOR_TAG2))
				  foundTumor=true;
			}
			if (!foundTumor) {
				SomaticCombiner.logger.log(Level.WARNING,"Warning: "+filePath+" is skipped due to no TUMOR or TUMOUR found in the samplename line!");

				return 0;
			}
		}
		else
			if (sampleNames.size()>2) {
				SomaticCombiner.logger.log(Level.WARNING,"Warning: "+filePath+" is skipped due to more than two samples in the VCF!");
				return 0;
			}
		int count=0;
		mHeader=processHeader();	
		// System.out.println(ss);
		final CloseableIterator<VariantContext> variantIterator = vcfFileReader.iterator();
		while (variantIterator.hasNext()) {
			final VariantContext vc = variantIterator.next();
//			if (vc.getStart()==21233587 && vc.getContig().contentEquals("chr1"))
//				System.out.println("found!");
		//	SomaticCombiner.logger.log(Level.INFO,caller+" "+vc.getContig()+":"+vc.getStart());
			// Set<String> filters=vc.getFilters();
			
			Boolean ff = vc.isFiltered();
			Set<String> filters = vc.getFilters();
			String filterContent = "";
//			if (caller.equals("Somaticsniper"))
//				ff=false;

			if (!filters.isEmpty()) {
				Iterator<String> iter = filters.iterator();
				while (iter.hasNext()) {
					filterContent = filterContent + caller + "_" + iter.next() + ",";
				}
				filterContent = filterContent.substring(0, filterContent.length() - 1);
			}

			if (ff == false) {
				count++;
				if (vc.isBiallelic()) {
					Variant variant=new Variant(vc,caller,priority,set);
					list.add(variant);
				}
				else {
					//Muse or MuTect2 split allele and genotypes
					List<Allele> alleles = vc.getAlleles();
					
					int alleleCount=alleles.size();
					Variant variant=new Variant(vc,caller,priority,set);
					for (int i=1;i<alleleCount;i++) {
					  VariantContextBuilder build=new VariantContextBuilder();
					  VariantContext splitVC=null;
					  VariantContext tmpVC=variant.getVariantContext();
					  List<Allele> tmpAlleles=new ArrayList<Allele>(tmpVC.getAlleles());
					//  tmpAlleles=tmpVC.getAlleles();
					  List<Genotype> tmpGenotypes=new ArrayList<Genotype>(tmpVC.getGenotypes());
					 
					  for (int j=alleleCount-1;j>0;j--) 
						 if ((j!=i) && (j!=0)) 
					       tmpAlleles.remove(j);
					  for (int j=0;j<tmpAlleles.size();j++) 
						  SomaticCombiner.logger.log(Level.FINEST,tmpAlleles.get(j).getBaseString());
						  				  
					  List<Genotype> splitGenotypes=new ArrayList<Genotype>();
					  for (Genotype gt:tmpGenotypes) {
						  if (gt.hasAD()) {
							  GenotypeBuilder splitGenotypeBuilder=new GenotypeBuilder();
							  
							  int[] ADs=gt.getAD();
							  int[] newADs=new int[2];
							  for (int j=0,k=0;j<alleleCount;j++) 
									 if ((j==i) || (j==0)) 
								       newADs[k++]=ADs[j];									   
							  
							  List<Allele> tmpGtAlleles=new ArrayList<Allele>(gt.getAlleles());
							  // SomaticCombiner.logger.log(Level.FINEST,"Allele size:"+vc.getGenotype(0).getAlleles().size());
							  for (int j=tmpGtAlleles.size()-1;j>=0;j--) {
								  boolean found=false;
								  for (int k=0;k<tmpAlleles.size();k++) {
									  if (tmpGtAlleles.get(j).equals(tmpAlleles.get(k))) {
										  found=true;
										  break;
									  }
								  }
								  if (!found) {
								//	  delIndices.add(j);
									  tmpGtAlleles.remove(j); 
								//	  break;
								  }  
							  }
							  
							  // following is to handle 2/2 situation
							  for (int j=tmpGtAlleles.size()-1;j>=0;j--) {
								  boolean found=false;
								  for (int k=0;k<tmpAlleles.size();k++) {
									  if (tmpGtAlleles.get(j).equals(tmpAlleles.get(k))) {
										  found=true;
										  break;
									  }
								  }
								  if (!found) {
								//	  delIndices.add(j);
									//  tmpGtAlleles.get(j).
									  tmpGtAlleles.remove(j);
									//  break;
								  }  
							  }
							 
							  if (tmpGtAlleles.size()==0) {
								  tmpGtAlleles.add(Allele.create(".",false));
								  splitGenotypeBuilder.alleles(tmpGtAlleles);
								  splitGenotypeBuilder.AD(newADs);
								  splitGenotypeBuilder.DP(0);
								   
							  }
							  else {
								 // SomaticCombiner.logger.log(Level.FINEST,"Allele size:"+vc.getGenotype(0).getAlleles().size());
								 // for (int j=0;j<tmpGtAlleles.size();j++) 
								//	  SomaticCombiner.logger.log(Level.FINEST,"genotype:"+tmpGtAlleles.get(j).getBaseString());
									  							  
								  splitGenotypeBuilder.alleles(tmpGtAlleles);
								  splitGenotypeBuilder.AD(newADs);
								  if (gt.hasDP()) splitGenotypeBuilder.DP(gt.getDP());	
							  }
							  Map<String,Object> gtExtendedAttributes=gt.getExtendedAttributes();
							  Map<String,Object> gtTmpExtendedAttributes=new HashMap<String,Object>(gt.getExtendedAttributes());
						      for(String key:gtExtendedAttributes.keySet()) {
						    	  String cName=gtExtendedAttributes.get(key).getClass().getName();
//								  System.out.print(cName);
								  if (key.equals("AF")) {
									  String AFs[]=gtExtendedAttributes.get(key).toString().split(",");
									  for (int j=0;j<alleleCount-1;j++)
										  if (j+1==i)
											  gtTmpExtendedAttributes.replace(key,AFs[j]);
									  
								  }
								  else {
									  if (cName.contains("ArrayList")){
											ArrayList fieldsForKey=(ArrayList) gtExtendedAttributes.get(key);
											if (fieldsForKey.size()==alleleCount) 
												for (int j=1;j<alleleCount;j++) 
													 if ((j!=i) && (j!=0)) 
														 fieldsForKey.remove(j);																		
									  }
									  
									  
								  }
						      }
						      splitGenotypeBuilder.name(gt.getSampleName());
						      splitGenotypeBuilder.attributes(gtTmpExtendedAttributes);
						      splitGenotypes.add(splitGenotypeBuilder.make());
							  
						  }
						  else 
							  splitGenotypes.add(gt);
						  						 						 
					  }
					  build.alleles(tmpAlleles);					  
					  build.chr(vc.getContig());
					  build.start(vc.getStart());
					  build.id(vc.getID());
					  
					  if (vc.hasLog10PError()) { 
						  String qual=String.valueOf(vc.getPhredScaledQual());
						  Map<String,Object> tmpInfo=new HashMap<String,Object>();
						  tmpInfo=vc.getAttributes();
						  tmpInfo.put(QUAL_TAG, qual);
						  build.attributes(tmpInfo);
					  }
					  else
						  build.attributes(vc.getAttributes());
					  build.stop(vc.getEnd());
					  build.genotypes(splitGenotypes);
					  
					  splitVC=build.make();
					  Variant splitVariant=new Variant(splitVC,caller,priority,set);
					  list.add(splitVariant);
					}
				}
			    	
			}
    	}
		return count;
	}
	
	private String voting(Variant vv) {
		String voting="";
		String[] callers=vv.getCaller().split(",");
		for (int i=0;i<SomaticCombiner.callerSymbols.length();i++) {
			String s=SomaticCombiner.callerSymbols.substring(i,i+1);
			boolean found=false;
			for (int j=0;j<callers.length;j++) {
				if (SomaticCombiner.nameFromSymbol(s).equals(callers[j])) {
				   found=true;
				   break;
			    }
			}
			if (found) 
				voting+="1";
			else
				voting+="0";
		}
		return(voting);	
	
		
	}
	
	
	public void writeVariants(BufferedWriter bw,Variant vv, int snvCallerNum,int indelCallerNum,ArrayList<String> medianAFs) throws IOException, ClassNotFoundException {
//		if (vv.getVariantContext().getStart()==6263642)
//			System.out.println("found!");
//      This function for test only		
		Variant variant=new Variant(vv.getVariantContext(),vv.getCaller(),vv.getPriority(),vv.getSet());
		// variant.g
		bw.append(vv.getVariantContext().getContig()+"\t"+vv.getVariantContext().getStart()+"\t"+vv.getVariantContext().getID()+"\t"+
	              vv.getVariantContext().getAlleles().get(0).getBaseString()+"\t");
		
		if (vv.getVariantContext().getAlleles().size()>1)
			bw.append(vv.getVariantContext().getAlleles().get(1).getBaseString()+"\t");
		else
			bw.append(vv.getVariantContext().getAlleles().get(0).getBaseString()+"\t");
		
		Map<String,Object> info=vv.getVariantContext().getAttributes();
		String infoContent="";
		String format="";
		String gtString="";
		//vcfEncoder.write(bw, vv.getVariantContext());
		if (vv.getVariantContext().hasGenotypes()) {
		   Set<String> sampleNames=vv.getVariantContext().getSampleNames();
		   if ((sampleNames.size()!=2) && (sampleNames.size()!=0)) {
			   SomaticCombiner.logger.log(Level.WARNING,"Error: the samples in "+filePath+" not equals to 2 or 0! The variants in this file will be skipped!");
               return;
		   }
		   if (sampleNames.size()==2) {
			   String[] samples=new String[2];
			   boolean foundTumor=false;
			   for(String sampleName:sampleNames) 
				   if (sampleName.toUpperCase().contains(TUMOR_TAG1)||sampleName.toUpperCase().contains(TUMOR_TAG2)) {
					  samples[0]=sampleName;
					  foundTumor=true;  
				   }
				   else
					  samples[1]=sampleName;
			   if (!foundTumor) {
				   SomaticCombiner.logger.log(Level.WARNING,"Error: the samples in "+filePath+" does not contain TUMOR in the genotype columns! The variants in this file will be skipped!");
	               return; 
			   }
			   Set<String> extendedGtKeyset=new TreeSet<String>();
			   for(int i=0;i<samples.length;i++) {
				   Genotype gt=vv.getVariantContext().getGenotype(samples[i]);
				   Map<String,Object> extendedAttributes=gt.getExtendedAttributes();
				   for (String key:extendedAttributes.keySet()) {
					   extendedGtKeyset.add(key);
				   }
			   }
			   for(int i=0;i<samples.length;i++) {
				   Genotype gt=vv.getVariantContext().getGenotype(samples[i]);

				   if (i==0) 
					   if (gt.getAlleles().size()>0) format="GT";
				   
				  
				   if (gt.isCalled()) {
					   if (gt.isHom()){							
							if (gt.isHomRef())
								gtString+="0/0";  // 0/0
							if (gt.isHomVar())
								gtString+="1/1";  // 1/1
						}
						if (gt.isHet()){
							if (gt.isHetNonRef())
								gtString+="1/2";  // 1/2 e.g.
							else
								gtString+="0/1";;  // 0/1
						}						
					}
						   
		
				   if (gt.hasDP()) {
					   if (i==0) {
						   if (format.length()==0)
							   format="DP";
						   else
							   format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+"DP";
					   }
					   if (gt.getAlleles().size()==0)
						   gtString=gtString+gt.getDP();
					   else
				           gtString=gtString+":"+gt.getDP();
				   }
			
				   if (gt.hasAD()) {  
					   gtString=gtString+":"+Arrays.toString(gt.getAD()).replace("[", "").replace("]", "").replace(" ","").trim();
					   if (i==0) format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+"AD";   
				   }
				   Map<String,Object> extendedAttributes=gt.getExtendedAttributes() ;
				   for (String key:extendedGtKeyset) {
					   if (i==0) {
						   if (vv.getCaller().contains(","))
					          format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+key;
						   else
							  format=format+VCFConstants.GENOTYPE_FIELD_SEPARATOR+SomaticCombiner.callerName(vv.getCaller())+"_"+key;
					   }
					   if (extendedAttributes.containsKey(key))
					      gtString=gtString+VCFConstants.GENOTYPE_FIELD_SEPARATOR+extendedAttributes.get(key).toString().replace(" ","").trim();
					   else
						  gtString=gtString+VCFConstants.GENOTYPE_FIELD_SEPARATOR+".";
				   }
				   
				   gtString=gtString+"\t";
			   }   
			}
		}
		else {
			format="GT";
			gtString="0/1\t0/0";
		}
		// List<String> keysSorted = info.keySet().stream().collect(Collectors.toList());
		List<String> aList = info.keySet().stream().collect(Collectors.toList());
		aList.sort(Comparator.naturalOrder());
		for (String key:aList) {		
			if (Integer.bitCount(vv.getSet())==1)
				infoContent=infoContent+SomaticCombiner.callerName(vv.getCaller())+"_"+key+"="+info.get(key).toString().replace("[", "").replace("]", "").replace(" ","").trim()+VCFConstants.INFO_FIELD_SEPARATOR;
			else
		        infoContent=infoContent+key+"="+info.get(key).toString().replace("[", "").replace("]", "").replace(" ","").trim()+VCFConstants.INFO_FIELD_SEPARATOR;			
		}
		bw.append(".\t");
		String WESPass="ADJ_LowConf";
		float tumorAF=0;
		int tumorDP=0;
		if (vv.getVariantContext().isSNP()) {
			tumorAF=vv.getTumorAF();
			tumorDP=vv.getTumorDP();
			
			
			if ((float)Integer.bitCount(vv.getSet()) / snvCallerNum >= 0.5) 
				WESPass="ADJ_PASS";
			// deep sequencing
			// if (vv.getCaller().contains("strelka") && vv.getCaller().contains("lofreq") || vv.getCaller().contains("strelka") && vv.getCaller().contains("vardict")
			//		  || vv.getCaller().contains("vardict") && vv.getCaller().contains("lofreq"))
			else {
				Iterator<String> iter=medianAFs.iterator();
				Boolean found=false;
				while (iter.hasNext()) {
					String aa[]=iter.next().split(":");
					if (vv.getVariantContext().getContig().equals(aa[0]) && vv.getVariantContext().getStart()>Integer.parseInt(aa[1]) && vv.getVariantContext().getStart()<=Integer.parseInt(aa[1])+500) {
						found=true;
						break;
					}				
				}
				if (found) {			
				    if (tumorAF<0.03 && tumorAF>0 && tumorDP>10 && vv.getCaller().contains("mutect2") )
						WESPass="ADJ_PASS";
				    if (tumorAF<=0.1 && tumorAF>=0.03 && tumorDP>10 && vv.getCaller().contains("mutect2") && vv.getCaller().contains("strelka"))	
						WESPass="ADJ_PASS";
				}
			}

		}
		else {
			tumorAF=vv.getTumorAF();
			tumorDP=vv.getTumorDP();
			if ((float)Integer.bitCount(vv.getSet()) / indelCallerNum >= 0.5)
				WESPass="ADJ_PASS";

		}
		
		if (vv.getVariantContext().isSNP()) {
			if ((float)Integer.bitCount(vv.getSet()) / snvCallerNum >= 0.5)
				bw.append("PASS" + ";"+WESPass+"\t");
			else
				bw.append("LowConf" + ";"+WESPass+"\t");
		} else {
			if ((float)Integer.bitCount(vv.getSet()) / indelCallerNum >= 0.5)
				bw.append("PASS"  + ";"+WESPass+"\t");
			else
				bw.append("LowConf"  + ";"+WESPass+ "\t");
		}
						
		bw.append(infoContent);
		// if (vv.getVariantContext().isSNP()) {
		if (tumorAF!=-1)
		    bw.append("Tumor_AF="+tumorAF+";");
		if (tumorDP>0)
			bw.append("Tumor_DP="+tumorDP+";");
			
		// }
		bw.append(SomaticCombiner.COUNT_TAG+"="+Integer.bitCount(vv.getSet())+VCFConstants.INFO_FIELD_SEPARATOR+SomaticCombiner.callerSymbols+"="+voting(vv));
		
		bw.append("\t"+format);
		if (gtString.endsWith("\t")) {
			gtString = gtString.substring(0, gtString.length()-1);
		}
		bw.append("\t"+gtString);
		
		
		bw.newLine();
		bw.flush();
	}
	
}
