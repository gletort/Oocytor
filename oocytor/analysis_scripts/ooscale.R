#rm( list = ls() )

source("ooNotNormal.R")

normsd = function(x)
{
	sd(x)/mean(x)
}

scaling = function( vec )
{
	meanv = mean(vec)
	sdv = sd(vec)
	return ( list((vec - mean(vec))/sd(vec), meanv, sdv ) )
	#return ( (vec - min(vec))/(max(vec)-min(vec)) )
}

scaleFromLoad = function( vec, scaling )
{
	mean = as.numeric(scaling[1])
	sd = as.numeric(scaling[2])
	return ( (vec - mean)/sd )
}

## Tukey test outlier detection
treatOutlier = function(vec, num, time)
{
	#return(vec)
	fact = 5

	ql = quantile(vec, 0.05)
	qh = quantile(vec, 0.95)
	inter = qh-ql
	if ( inter<= 0 )
	{
		return(vec)
	}
	lowlim = ql-fact * inter
	highlim = qh+fact*inter
	#print(sum(vec>highlim) )
	#print(sum(vec<lowlim) )

	normal = (vec<=highlim) & (vec>=lowlim)
	
	## replace higher
	highout = which(vec>highlim)
	for ( i in highout )
	{
		hnum = num[i]
		keep = (num==hnum) & normal
		if (sum(keep)<1)
		{
			keep = normal
		}
		meanval = mean( vec[keep] )
		vec[i] = meanval
	}
	## replace lower
	lowout = which(vec<lowlim)
	for ( i in lowout )
	{
		hnum = num[i]
		keep = (num==hnum) & normal
		if (sum(keep)<1)
		{
			keep = normal
			#print(paste("Ovo to check ", hnum, " time ", time[i], sep="") )
		} 
		meanval = mean( vec[keep] )
		vec[i] = meanval
		
	}
	#print(sum(vec>highlim) )
	#print(sum(vec<lowlim) )

	return(vec)
}

projData = function( data, tmin, tmax )
{
	if ( tmin<tmax )
	{
		subdatas = data[(data$Time>=tmin) & (data$Time<=tmax),]
	} else {
		subdatas = data
	}
	sdatas = aggregate( subdatas, list(subdatas$Num), mean, na.rm=T, na.action=NULL )
	rem = which( names(sdatas)=="Group.1" )
	sdatas = sdatas[,-c(rem)]
	return (sdatas)
}
	

scaleSubset = function( data, tmin, tmax, loading, loadedScale, avg )
{
	if ( tmin<tmax )
	{
		subdatas = data[(data$Time>=tmin) & (data$Time<=tmax),]
	} else {
		subdatas = data
	}
	if ( avg > 0 )
	{
	sdatas = aggregate( subdatas, list(subdatas$Num), mean, na.rm=T, na.action=NULL )
	rem = which( names(sdatas)=="Group.1" )
	sdatas = sdatas[,-c(rem)]
	} else {
		sdatas = subdatas
	}
	
	## Scaling, h-mean/sd, can also do h-min/max-min
	# to do compare both, according to methods use afterwards
	nfeat = names(sdatas)
	scardatas = c()
	scales = c()
	for ( i in 1:length(nfeat) ) 
	{
	## column not normalisable
	if ( notNormal(nfeat[i]) ) 
	{
		scardatas = cbind(scardatas, sdatas[,i])
		scales = rbind(scales, c(0, 1) )
	}
	else
	{
		#print(nfeat[i])
		cur = treatOutlier(sdatas[,i], sdatas$Num, sdatas$Time)
		if (loading==0)
		{
			scalingres = scaling(cur)
			scardatas = cbind(scardatas, scalingres[[1]])
			scmean = scalingres[[2]]
			scsd = scalingres[[3]]
			scales = rbind(scales, c(scmean, scsd) )
		}
		else
		{
			j = which( row.names(loadedScale)== nfeat[i] )
			scardatas = cbind(scardatas, scaleFromLoad(cur, loadedScale[j,]))
			scales = rbind(scales, c(0, 1) )
		}
	}
	}
	colnames(scardatas) = nfeat
	rownames(scales) = nfeat
	
	return (list(scardatas, scales))
}

load_scale = function( src, namedata, loading, resfold )
{
	if ( loading == 0 )
	{	 
		return( NULL ) 
	} 
	filedata = read.table(file=paste(resfold,"/", src, namedata, "_scaling.txt",sep=""), header=T, sep="\t")
	return( filedata )
}

doScale = function( resfold, movoname, movonamesrc, load )
{
	rdatas = read.table(paste(resfold,"/", movoname, "_rawDatas.txt",sep=""), header=T, sep="\t")
	meandatas = read.table(paste(resfold,"/", movoname, "_meanDatas.txt",sep=""), header=T, sep="\t")

	## Scaling, h-mean/sd, can also do h-min/max-min
	# to do compare both, according to methods use afterwards
	loadScal = load_scale(movonamesrc, "_scaledMeanDatas", load, resfold )
	scadatas = scaleSubset( meandatas, 0, 0, load, loadScal, 1)
	write.table(file=paste(resfold,"/", movoname, "_scaledMeanDatas.txt",sep=""), scadatas[[1]], row.names=F, sep="\t")
	if (load == 0)
	{
		write.table(file=paste(resfold,"/", movoname, "_scaledMeanDatas_scaling.txt",sep=""), scadatas[[2]], row.names=T, sep="\t")
	}	

	## deb datas
	loadScal = load_scale(movonamesrc, "_scaledDebDatas", load, resfold )
	scaddatas = scaleSubset( rdatas, 6.5, 6.5, load, loadScal, 1)
	write.table(file=paste(resfold,"/", movoname, "_scaledDebDatas.txt",sep=""), scaddatas[[1]], row.names=F, sep="\t")
	if (load == 0)
	{
		write.table(file=paste(resfold,"/", movoname, "_scaledDebDatas_scaling.txt",sep=""), scaddatas[[2]], row.names=T, sep="\t")
	}


	## all datas
	loadScal = load_scale(movonamesrc, "_scaledDatas", load, resfold )
	scaddatas = scaleSubset( rdatas, -10, 70, load, loadScal, 0)
	write.table(file=paste(resfold,"/", movoname, "_scaledDatas.txt",sep=""), scaddatas[[1]], row.names=F, sep="\t")
	if (load == 0)
	{
		write.table(file=paste(resfold,"/", movoname, "_scaledDatas_scaling.txt",sep=""), scaddatas[[2]], row.names=T, sep="\t")
	}

}
