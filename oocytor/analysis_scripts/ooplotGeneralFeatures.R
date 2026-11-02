
plotTimeFeature = function(data, feat, tmin, tmax)
{
	indfeat = which(names(data)==feat)
	allfeat = data[,indfeat]
	data[, indfeat] = outlierByFeat(allfeat, data$Time)
	data = data[data$Time>=tmin&data$Time<=tmax,]
	data$Type = factor(data$Type, levels=c(1,2))

	p = ggplot(data, aes(x=Time, y=get(feat), group=Num, colour=Type) )	
	p = p + geom_smooth(data=data, aes(x=Time, y=get(feat), group=Type, colour=Type, linetype=Type), method="gam",n=2000, size=1.8)
	p = p + scale_colour_manual(values=myCol )
	p = p + scale_linetype_manual(values = c("solid", "solid"))
	p = p +theme(legend.position="none") 
	p = p + geom_vline(xintercept=0, linetype="dashed", color="gray60", size=0.5)  
	p = p + ylab(feat)
	p = p + theme_pub()
	p = p + xlab("Time (min)")
	#p = p + ylim(0,40)
	return(p)
}

plotTimeToGPFeature = function(data, feat, tmin, tmax)
{
	indfeat = which(names(data)==feat)
	allfeat = data[,indfeat]
	data[, indfeat] = outlierByFeat(allfeat, data$TimeToGP)
	data = data[data$TimeToGP>=tmin&data$TimeToGP<=tmax,]
	data$Type = factor(data$Type, levels=c(1,2))

	p = ggplot(data, aes(x=TimeToGP, y=get(feat), group=Num, colour=Type) )	
	p = p + geom_smooth(data=data, aes(x=TimeToGP, y=get(feat), group=Type, colour=Type, linetype=Type), method="gam", n=2000, size=1.8)
	p = p + scale_colour_manual(values=myCol )
	p = p + scale_linetype_manual(values = c("solid", "solid"))
	p = p +theme(legend.position="none") 
	p = p + geom_vline(xintercept=0, linetype="dashed", color="gray60", size=0.5)  
	p = p + ylab(feat)
	p = p + theme_pub()
	p = p + xlab("Time to GP (min)")
	#p = p + ylim(0,40)
	return(p)
}


plotTimeToGPFeatureIndiv = function(data, feat, tmin, tmax)
{
	indfeat = which(names(data)==feat)
	allfeat = data[,indfeat]
	data[, indfeat] = outlierByFeat(allfeat, data$TimeToGP)
	data = data[data$TimeToGP>=tmin&data$TimeToGP<=tmax,]
	#data$Score = scores
    
	p = ggplot(data, aes(x=TimeToGP, y=get(feat), group=Num, colour=Score) )	
	p = p + geom_smooth(data=data, aes(x=TimeToGP, y=get(feat), group=Num, colour=Score), method="gam", n=2000, size=1.5)
	#p = p + scale_colour_manual(values=myCol )
    p = p + scale_color_gradient(low="green", high="red")
	#p = p + scale_linetype_manual(values = c("solid", "solid"))
	p = p +theme(legend.position="none") 
	p = p + geom_vline(xintercept=0, linetype="dashed", color="gray60", size=0.5)  
	p = p + ylab(feat)
	p = p + theme_pub()
	p = p + xlab("Time to GP (min)")
	#p = p + ylim(0,40)
	return(p)
}

plotFeatureFeature = function(data, feat, ofeat, tmin, tmax)
{
	#p = ggplot(data, aes(x=Time, y=OoPerim/(pi), group=Expulsion, colour=Expulsion) )	
	#for ( n in unique(data$Num) )
	#{
	#	deb = min(data$Time[data$Num==n])
	#	data$OoPerim[data$Num==n] = data$OoPerim[data$Num==n]/data$OoPerim[data$Num==n&data$Time==deb]
	#}
	data = data[data$TimeToGP>=tmin&data$TimeToGP<=tmax,]
    mcdata = aggregate( data, list(data$Num), mean )
	ct = (tmax+tmin)/2
	mcdata$Time = ct
	mcdata = as.data.frame(mcdata)
	mcdata$Type = as.factor(mcdata$Type)

    #subdata = data[seq(1,length(data[,1]), 4),]
	p = ggplot(mcdata, aes(x=get(feat), y=get(ofeat), group=Num, colour=Type) )	
	p = p + geom_point(size=10, alpha=1)
	#p = p + geom_smooth(data=subdata, aes(x=get(feat), y=get(ofeat), group=Type, colour=Type), method="loess",span=0.3, size=1.8)
	#p = p + scale_colour_manual(breaks=c(FALSE,TRUE), values=c("darkred", "darkgreen") )
	p = p +theme(legend.position="none") 

	p = p + theme_mywh()

		#p = p + ylim(61,84)
    indfeat = which(names(mcdata)==feat)
    oindfeat = which(names(mcdata)==ofeat)
    
    print(cor.test(mcdata[,indfeat], mcdata[,oindfeat]))
		return(p)
}

plotFeatureFeatureScore = function(data, feat, ofeat, tmin, tmax)
{
	data = data[data$TimeToGP>=tmin&data$TimeToGP<=tmax,]
    mcdata = aggregate( data, list(data$Num), mean )
	ct = (tmax+tmin)/2
	mcdata$Time = ct
	mcdata = as.data.frame(mcdata)
	mcdata$Type = as.factor(mcdata$Type)

	p = ggplot(mcdata, aes(x=get(feat), y=get(ofeat), group=Num, colour=Score) )	
	p = p + geom_point(size=10, alpha=1)
	p = p +theme(legend.position="none") 

	p = p + theme_mywh()
    p = p + scale_color_gradient(low="green", high="red")
    p = p + scale_fill_gradient(low="green", high="red")
		#p = p + ylim(61,84)
    indfeat = which(names(mcdata)==feat)
    oindfeat = which(names(mcdata)==ofeat)
    
    print(cor.test(mcdata[,indfeat], mcdata[,oindfeat]))
		return(p)
}

plotFeatFeature = function(data, feat, ofeat)
{
	#p = ggplot(data, aes(x=Time, y=OoPerim/(pi), group=Expulsion, colour=Expulsion) )	
	#for ( n in unique(data$Num) )
	#{
	#	deb = min(data$Time[data$Num==n])
	#	data$OoPerim[data$Num==n] = data$OoPerim[data$Num==n]/data$OoPerim[data$Num==n&data$Time==deb]
	#}
	subdata = data[seq(1,length(data[,1]), 4),]
	p = ggplot(data, aes(x=get(feat), y=get(ofeat), group=Num, colour=Type) )	
	p = p + geom_line(size=1, alpha=0.2)
	#p = p + geom_smooth(data=subdata, aes(x=get(feat), y=get(ofeat), group=Type, colour=Type), method="loess",span=0.3, size=1.8)
	#p = p + scale_colour_manual(breaks=c(FALSE,TRUE), values=c("darkred", "darkgreen") )
	p = p +theme(legend.position="none") 

	p = p + theme_mywh()

		#p = p + ylim(61,84)

		return(p)
}

plotFeatFeatureScore = function(data, feat, ofeat)
{
	#p = ggplot(data, aes(x=Time, y=OoPerim/(pi), group=Expulsion, colour=Expulsion) )	
	#for ( n in unique(data$Num) )
	#{
	#	deb = min(data$Time[data$Num==n])
	#	data$OoPerim[data$Num==n] = data$OoPerim[data$Num==n]/data$OoPerim[data$Num==n&data$Time==deb]
	#}
	subdata = data[seq(1,length(data[,1]), 4),]
	p = ggplot(data, aes(x=get(feat), y=get(ofeat), group=Num, colour=Score) )	
	p = p + geom_line(size=1, alpha=0.2)
	p = p + scale_color_gradient(low="green", high="red")
    p = p +theme(legend.position="none") 

	p = p + theme_mywh()

		#p = p + ylim(61,84)

		return(p)
}
	

## Plot Feature according to Type
plotTypeFeature = function(data, tmin, tmax, feat)
{
	if (tmax> tmin)
	{
		cdata = data[ (data$Time>=tmin) & (data$Time<tmax), ]
		mcdata = aggregate( cdata, list(cdata$Num), mean )
		ctitle = paste("",tmin," ",tmax, sep="")
	}
	else
	{
		mcdata = data
		ctitle=""
	}
	
	p = ggplot(mcdata, aes(x=Type, y=get(feat), group=Type, colour=Type) )	
	p = p + geom_violin(alpha=0.3,size=1)
	p = p + geom_jitter(aes(color=Type),width=0.15, size=2) 
	p = p + labs(y=feat)
	p = p + scale_x_continuous(breaks=c(0,1), labels=c("No","Yes"))
	p = p + scale_colour_gradient(low="darkorange1", high="darkslateblue")
	p = p + ggtitle(ctitle)
	p = p + theme_mywh() 

	indfeat = which(names(mcdata)==feat)
	ymax = max(mcdata[,indfeat])	
	ks = ks.test( mcdata[mcdata$Type==1, indfeat], mcdata[mcdata$Type==2, indfeat] )
	p = p + geom_signif( annotation=formatC(ks$p.value, digits=2), y_position=ymax*1.01, xmin=0, xmax=1 )
	return(p)
}



## Plot Feature according to Expulsion or not
boxplotFeature = function(data, feat, time, tmin, tmax)
{
    data = data[time>=tmin&time<=tmax,]
	mcdata = aggregate( data, list(data$Num), mean )
	ct = (tmax+tmin)/2
	mcdata$Time = ct
	mcdata = as.data.frame(mcdata)
	mcdata$Type = as.factor(mcdata$Type)
	
	p = ggplot(mcdata, aes(x=Type, y=get(feat), fill=Type, color=Type) )	
	p = p + geom_violin( position=position_dodge(width = 0.7), alpha=0.3,size=1)
	p = p + geom_point(pch = 21, position = position_jitterdodge())
	p = p + labs(y=feat, title=paste("At ",ct,"min", sep=""))
	p = p + scale_colour_manual(values=myCol )
	p = p + scale_fill_manual(values=myCol )
	p = p + theme_mywh() 

	indfeat = which(names(mcdata)==feat)
	ymax = max(mcdata[,indfeat])	
	
    if (sum(mcdata$Type==1)>0)
	{
		ks = ks.test( mcdata[mcdata$Type==1, indfeat], mcdata[mcdata$Type==2, indfeat] )
		p = p + geom_signif( annotation=formatC(ks$p.value, digits=2), color=myCol[1], y_position=ymax*0.95, xmin=1, xmax=2 )
    }
    
    ymax = max(mcdata[,indfeat])
    ymin = min(mcdata[,indfeat])

    return (list(p, ymin, ymax))
    #return(p)
}


## Plot Feature according to Expulsion or not
boxplotFeatureOne = function(data, feat, time, tmin, tmax)
{
    data = data[time>=tmin&time<=tmax,]
	mcdata = aggregate( data, list(data$Num), mean )
	ct = (tmax+tmin)/2
	mcdata$Time = ct
	mcdata = as.data.frame(mcdata)
	mcdata$Type = as.factor(mcdata$Type)
	
	p = ggplot(mcdata, aes(x=Type, y=get(feat), fill=Num, color=Num) )	
	p = p + geom_violin( position=position_dodge(width = 0.7), alpha=0.3,size=1)
	p = p + geom_point(pch = 21, position = position_jitterdodge())
	p = p + labs(y=feat, title=paste("At ",ct,"min", sep=""))
	#p = p + scale_colour_manual(values=myCol )
	#p = p + scale_fill_manual(values=myCol )
	p = p + theme_mywh() 

    return(p)
}

## Plot Feature color by Score
boxplotFeatureOneScore = function(data, feat, time, tmin, tmax)
{
    data = data[time>=tmin&time<=tmax,]
	mcdata = aggregate( data, list(data$Num), mean )
	ct = (tmax+tmin)/2
	mcdata$Time = ct
	mcdata = as.data.frame(mcdata)
	mcdata$Type = as.factor(mcdata$Type)
	
	p = ggplot(mcdata, aes(x=Type, y=get(feat), fill=Score, color=Score) )	
	p = p + geom_violin( position=position_dodge(width = 0.6), alpha=0.3,size=1)
	p = p + geom_point(pch = 21, position = position_jitterdodge(), size=3)
	p = p + labs(y=feat, title=paste("At ",ct,"min", sep=""))
    p = p + scale_color_gradient(low="green", high="red")
    p = p + scale_fill_gradient(low="green", high="red")
	#p = p + scale_colour_manual(values=myCol )
	#p = p + scale_fill_manual(values=myCol )
	p = p + theme_mywh() 
    
    
	indfeat = which(names(mcdata)==feat)
	ymax = max(mcdata[,indfeat])
    ymin = min(mcdata[,indfeat])

    return (list(p, ymin, ymax))
}


## Plot Feature color by Score
boxplotFeatureCompScore = function(data, feat, time, tmin, tmax, tmin2, tmax2)
{
    
    data2 = data[time>=tmin2&time<=tmax2,]
	mcdata2 = aggregate( data2, list(data2$Num), mean )
	ct2 = (tmax2+tmin2)/2
	mcdata2$Time = ct2
	mcdata2 = as.data.frame(mcdata2)
	mcdata2$Type = as.factor(mcdata2$Type)
    
    data = data[time>=tmin&time<=tmax,]
	mcdata = aggregate( data, list(data$Num), mean )
	ct = (tmax+tmin)/2
	mcdata$Time = ct
	mcdata = as.data.frame(mcdata)
	mcdata$Type = as.factor(mcdata$Type)
    
    indfeat = which(names(mcdata)==feat)
	mcdata[,indfeat] = mcdata[,indfeat]/mcdata2[,indfeat]
	
	p = ggplot(mcdata, aes(x=Type, y=get(feat), fill=Score, color=Score) )	
	p = p + geom_violin( position=position_dodge(width = 0.6), alpha=0.3,size=1)
	p = p + geom_point(pch = 21, position = position_jitterdodge(), size=3)
	p = p + labs(y=feat, title=paste("At ",ct,"min", sep=""))
    p = p + scale_color_gradient(low="green", high="red")
    p = p + scale_fill_gradient(low="green", high="red")
	#p = p + scale_colour_manual(values=myCol )
	#p = p + scale_fill_manual(values=myCol )
	p = p + theme_mywh() 
    
    
	ymax = max(mcdata[,indfeat])
    ymin = min(mcdata[,indfeat])

    return (list(p, ymin, ymax))
}