rm( list = ls() )

movo = "hum"
library("zoo")
folds = c()
#folds = c(folds, "../data/09.01.2023/")
#folds = c(folds, "../data/02.02.2023/")
#folds = c(folds, "../data/08.02.2023/")
#folds = c(folds, "../data/_23.03.23/")
#folds = c(folds, "../data/_22.03.23/")
#folds = c(folds, "../data/_17.04.23/")
#folds = c(folds, "../data/_13.04.23/")

#folds = c(folds, "../data/n_16.01.24/")
#folds = c(folds, "../data/n_12.12.23/")
#folds = c(folds, "../data/n_19.12.23/")


#folds = c(folds, "../dataHuman/tredo/treated/")
folds = c(folds, "../dataHuman/treated/recrop/")


#folds = c(folds, "../dataComp/Ana/_13.04.23/")
#folds = c(folds, "../dataComp/Ana/_17.04.23/")
#folds = c(folds, "../dataComp/Ana/_22.03.23/")
#folds = c(folds, "../dataComp/Ana/_23.03.23/")

#folds = c(folds, "../dataComp/Ana/02.02.2023/")
#folds = c(folds, "../dataComp/Ana/08.02.2023/")
#folds = c(folds, "../dataComp/Ana/09.01.2023/")
#folds = c(folds, "../dataComp/Ana/16.01.2023/")

#folds = c(folds, "../dataComp/Ana/n_12.12.23/")
#folds = c(folds, "../dataComp/Ana/n_16.01.24/")
#folds = c(folds, "../dataComp/Ana/n_19.12.23/")

#folds = c(folds, "../dataComp/Miv/")
#folds = c(folds, "../dataComp/Mivbis/")
#folds = c(folds, "../dataComp/Mivter")
#folds = c(folds, "../dataComp/MII/13.04.23/")
#folds = c(folds, "../dataComp/MII/17.04.23/")
#folds = c(folds, "../dataComp/MII/22.03.23/")
#folds = c(folds, "../dataComp/MII/23.03.23/")


circmean = function(x, hwinsize)
{
	res = c()
	for (ind in 1:length(x))
	{
		mres = mean(as.numeric(circvec(x, hwinsize, ind)))
		res = c(res, mres)
	}
	return(res)
}

circvec = function(x, hwinsize, ind)
{
	nx = length(x)
		miw = ind - hwinsize
		if ( miw <= 0 )
		{
			vec = x[c((nx+miw):nx,1:(ind+hwinsize))]
			return (vec)
		}
		maw = ind + hwinsize
		if (maw > nx)
		{
			return ( x[c(miw:nx,1:(maw-nx))] )
		}
		return (x[miw:maw])
}

movmean = function(x, size)
{
	res = rollapply(x, width = size, FUN = mean, align = "center", partial = TRUE)
	return(res)
}

for (fold in folds)
{

resume <- read.table(file=paste(fold,"/liste.csv", sep=""), sep=",", header=T)

mz = 0

num = c()
mfluc = c()
sfluc = c()
nfluc = c()
rfluc = c()
correlationL = c()
correlationC = c()
    
    
for ( i in 1:length(resume$FileName) )
{

    #if ( resume$DivideInPlane[i] < 1 )
    #    next
	fullnom = resume$FileName[i]
	nom = substring(fullnom, 0, nchar(as.character(fullnom))-4)
	#print(nom)

	flucdatas = read.table( paste(fold, "/measures/", nom, "_fluctuationResults.csv", sep=""), header=T, sep=",")

	nang = length(flucdatas[1,])-1
	dang = 2*pi/nang
	ntime = length(flucdatas[,1])
	size = 20    ## rpevisouly 50

	meaned = c()
    smoothdatas = flucdatas
	for (an in 1:nang)
	{
		smoothdatas[,an+1] = movmean( flucdatas[,an+1], 3 ) ## smooth in time , preivouslys 5
		mm = movmean( smoothdatas[,an+1], size )
		meaned = cbind(meaned, (smoothdatas[,an+1]-mm)/mm)
	}

	flucp = rbind(2*flucdatas[1,]-flucdatas[2,],2*flucdatas[1,]-flucdatas[2,], flucdatas)
	flucn = rbind(flucdatas, 2*flucdatas[ntime,]-flucdatas[ntime-1,], 2*flucdatas[ntime,]-flucdatas[ntime-1,])
	flucc = rbind(flucdatas[1,], flucdatas, flucdatas[ntime,])
	dfluc = (((flucc-flucp)+(flucn-flucc))/2.0)/abs(flucc)
    diffrads = abs(((flucc-flucp)+(flucn-flucc))/2.0)

	dfluc = dfluc[2:(ntime+1),]
	res = c()
   
    	
	for ( t in 1:length(dfluc[,1]) )
	{
		time = flucdatas[t,1]
		fluc = as.numeric(dfluc[t,-1])
		propmoving = sum( abs(fluc)>0.001 )/length(fluc)
		propgrowing = sum( fluc>0 )/length(fluc)
		
		spatfluc = as.numeric(flucdatas[t,-1])
		flucfft = fft(spatfluc-mean(spatfluc))
		mod = Mod(flucfft)
		mode = which.max(mod[1:(length(mod)/2)])
		power = mean(Mod(flucfft)^2)

		## auto-correlation length
		acfres = acf(spatfluc, lag.max=100, plot=F)
		if (length(which(acfres$acf<=0.1))>0)
		{
			acl = min(which(acfres$acf<=0.1)) * 360/length(spatfluc)
		}
		else
		{
			acl = 0
		}
		## fluctuation to local mean
		lflu = meaned[t,]
        
        rads = as.numeric(flucdatas[t,-1])
        meanrad = mean(rads)
        drads = as.numeric(diffrads[t,-1])

		vec = c(time, mean(abs(fluc)), sd(abs(fluc)), propmoving, propgrowing, mode, power, acl, mean(abs(lflu)), sd(abs(lflu)), mean(rads), sd(rads), mean(drads), sd(drads) )
		res = rbind(res, vec)
	}

	res = as.data.frame( res )
	names(res) = c("Time", "OoFluctuationMean", "OoFluctuationStd", "OoFluctuationPropMoving", "OoFluctuationPropGrowing", "OoFluctuationSpatialMode", "OoFluctuationSpatialPower", "OoFluctuationSpatialCorrLength", "OoFluctuationFromLocalMean", "OoFluctuationFromLocalStd", "OoRadiusMean", "OoRadiusStd", "OoRadiusDiffMean", "OoRadiusDiffStd")

	write.table(file=paste(fold, "/measures/", nom, "_meanFluctuations.csv",sep=""), res, row.names=F, sep=",")

}	


}
