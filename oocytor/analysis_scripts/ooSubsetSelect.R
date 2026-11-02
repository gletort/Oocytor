library(corrplot)
source("ooNotNormal.R")

subsetHighest = function( indatas )
{
## by removing correlated highest first
sdatas = indatas

sdatas = meandatas
for ( name in names(sdatas) )
{
	if (notNormal(name) )
	{
		sdatas[[name]]=NULL
	}
}

fcor <-cor(sdatas)
abcor = abs(fcor)
subset = sdatas
lratio = 0.98
while ( sum( abcor>lratio ) > length(subset[1,]) )
{
	colsum = colSums(abcor>lratio)
	ind = which.max(colsum)
	#print(sum(abcor>0.8))
	subset = subset[,-ind]
	#print(subset)
	abcor = abs( cor(subset) )
}
print(names(subset))

return( subset )
}


score = function(x, th)
{
	return (sum(x[x>th], na.rm=T)-1)
}

subsetCluster = function( indatas, clusratio )
{
## by removing correlated by cluster
cdatas = indatas

for ( name in names(indatas) )
{
	if (notNormal(name) )
	{
		cdatas[[name]]=NULL
	}
}

#print(cdatas)
fccor <-cor(cdatas)
#print(fccor)
abccor = abs(fccor)
clsubset = cdatas

# get highest
indmax = 0
cmax = 0.005
keep = c()
while (cmax > 0)
{
fccor <-cor(cdatas)
   # print(cdatas)
abccor = abs(fccor)
indmax = 0
cmax = 0
for ( i in 1:length(abccor[,1]) )
{
	sco = score(abccor[i,], clusratio)
	if ( sco > cmax )
	{
		cmax = sco
		indmax = i
	}
}
#print(abccor)
if ( cmax > 0 )
{
#eliminate
del = abccor[,indmax]>clusratio
#keepit
del[indmax]=F
cdatas = cdatas[,!del]
}
}


return(cdatas)
}

