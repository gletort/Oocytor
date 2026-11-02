
rm( list = ls() )
mode = "human"

if (mode=="mouse") {
    print("Doing mouse data")
    resf= "results202501_mouse"
    movo = "as"
    fold = "../data/"
    name_list = "liste.csv"
}


if (mode=="human"){
     print("Doing human data")
    resf= "results202511_humI"
    movo = "hum"
    fold = "../dataHuman/treated/"
    name_list = "liste_cleaned_recrop.csv"
}


if (mode=="humanII"){
     print("Doing human data II")
    resf= "results202511_humII"
    movo = "hum"
    fold = "../dataHuman/newdata/"
    name_list = "allliste_AS.csv"
}

if (mode=="comp") {
    print("Doing both data")
    resf= "resultsScale_2504"
    movo = "as"
    fold = "../dataComp/"
    name_list = "liste.csv" 
}

if (mode=="mII") {
    print("Doing mouse MII data")
    resf= "results202510_mII"
    movo = "as"
    fold = "../dataMII/compMII/"
    name_list = "liste.csv"
}


datas = c()
listfiles = list.files(fold, recursive=TRUE)

num = 1
fichage = c()
for ( file in listfiles )
{
	if (basename(file)== name_list)
    {
		print(file)
		resume = read.table( paste(fold, file, sep=""), sep=",", header=T)
		subfold = dirname(file)
        
		#if ( (subfold!="set11"))
		{
		for ( i in 1:length(resume[,1]) )
		{
            if (mode=="human" && resume$Exclus[i]==1)
            {
                next
            }
            if (mode=="humanII" && resume$DivideInPlane[i]<1)
            {
                next
            }

			if (mode =="humanII"){
                type = 1
                }
            else {
                type = resume$Type[i]
                }
			fullnom = resume$FileName[i]
            if (mode=="human"){
               ko = resume$ManualKO3[i]
            }
			nom = substring(fullnom, 0, nchar(as.character(fullnom))-4)
			mesname = paste( fold, subfold, "/measures/", nom, sep="")

			# Read analyses datas
			if ( file.exists( paste(mesname, "_oocyteFeatures.csv", sep="") ) )
			{
			mdatas = read.table( paste(mesname, "_oocyteFeatures.csv", sep=""), header=T, sep=",")
			mdatas$OoLocoEFACumDist = NULL
			mdatas$OoLocoEFAEntropy = NULL
			mdatas$OoLocoEFAMaxLnMode = NULL
            #mdatas$OoCurvatureCV = mdatas$OoCurvatureCoefVar
            #mdatas$OoCurvatureCoefVar = NULL
	
                
			## Add expulsion info
			if (mode=="human"){
                cdat = cbind( mdatas, rep(ko, length(mdatas[,1])), rep(type, length(mdatas[,1])), rep(num, length(mdatas[,1])) )
			nc = length(cdat[1,])
            names(cdat)[nc-2] = "ManualKO"
            names(cdat)[nc-1] = "Type"
			names(cdat)[nc] = "Num"
            } else {
                     cdat = cbind( mdatas, rep(type, length(mdatas[,1])), rep(num, length(mdatas[,1])) )
			nc = length(cdat[1,])
            names(cdat)[nc-1] = "Type"
			names(cdat)[nc] = "Num"
            }
            if (mode=="humanII")
            {
                divPlane = resume$DivideInPlane[i]
                check = resume$Check[i]
            cdat = cbind( cdat, rep(divPlane, length(mdatas[,1])), rep(check, length(mdatas[,1])))
			nc = length(cdat[1,])
            names(cdat)[nc-1] = "DivideInPlane"
            names(cdat)[nc] = "Check"
            }
                
			fich = c(nom, subfold, num)
			fichage = rbind(fichage, fich)
			num = num + 1

			## zp features
			#mdatas = read.table( paste(mesname, "_zpFeatures.csv", sep=""), header=T, sep=",")
			#cdat = merge( cdat, mdatas, by="Time")
	
			## periv features
			mdatas = read.table( paste(mesname, "_perivFeatures.csv", sep=""), header=T, sep=",")
			cdat = merge( cdat, mdatas, by="Time")

			## textures	
			mdatas = read.table( paste(mesname, "_textureFeatures.csv", sep=""), header=T, sep=",")
			cdat = merge( cdat, mdatas, by="Time")
			#mdatas = read.table( paste(mesname, "_lbpFeatures.csv", sep=""), header=T, sep=",")
			#cdat = merge( cdat, mdatas, by="Time")
	
			## dynamics
			# must run calcFluctuation.R before if not done yet
			mdatas = read.table( paste(mesname, "_meanFluctuations.csv", sep=""), header=T, sep=",")
			cdat = merge( cdat, mdatas, by="Time")
	    
            mdatas = read.table( paste(mesname, "_pivFeatures.csv", sep=""), header=T, sep=",")
			cdat = merge( cdat, mdatas, by="Time")
	
			## spatial
			mdatas = read.table( paste(mesname, "_spatialFeatures.csv", sep=""), header=T, sep=",")
			cdat = merge( cdat, mdatas, by="Time")
			#mdatas = read.table( paste(mesname, "_zpTubeFeatures.csv", sep=""), header=T, sep=",")
			#cdat = merge( cdat, mdatas, by="Time")

            ## curvatures, flatness
			# must run getCurvature.bsh and analyseCurvature.R before if not done yet
			#mdatas = read.table( paste(mesname, "_meanCurvatures.csv", sep=""), header=T, sep=",")
			#cdat = merge( cdat, mdatas, by="Time")
                
			#cdat$Time = cdat$Time-min(cdat$Time)	
			#tlim = min( tlim, max(cdat$Time) )	
			datas = rbind(datas, cdat)
			}
		}
		}
	}
}

datas = as.data.frame(datas)
rdatas = datas

write.table(file=paste(resf,"/", movo, "_rawDatas.txt",sep=""), rdatas, row.names=F, sep="\t")

meandatas = aggregate( rdatas, list(rdatas$Num), mean )
rem = which( names(meandatas)=="Group.1" )
meandatas = meandatas[,-c(rem)]
write.table(file=paste(resf,"/", movo, "_meanDatas.txt",sep=""), meandatas, row.names=F, sep="\t")

colnames(fichage) = c("Movie", "Folder", "Num")
write.table(file=paste(resf, "/", movo, "_whos.txt", sep=""), fichage, row.names=F, col.names=T, sep="\t")
