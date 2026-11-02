smote = function( indatas, nb )
{
	n = length(indatas[,1])
	if ( nb > n ) { nb = n }
	dists = as.matrix(dist(indatas) )
	ind = sample.int( n, nb )
	i = 1
	newpts = c()
	while ( i <= nb )
	{
		nei = knearest( dists[,ind[i]], 6 )
		indnei = sample.int( length(nei), 1)
		ptref = indatas[i,]
		ptnei = indatas[indnei,]
		newpt = ptref + runif(1,0,1) * (ptnei-ptref)
		i = i + 1
		newpts = rbind(newpts, newpt)
	}
	return (newpts)
}

knearest = function( dist, k )
{
	ord = order(dist)
	return (ord[2:(2+k)])
}
