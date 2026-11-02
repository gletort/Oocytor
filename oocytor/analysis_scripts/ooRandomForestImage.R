## tree method:  no need to normalise datas

rm( list = ls() )
fold="results202305"
name = "as1h"
source("ooSubsetSelect.R")
source("ooscale.R")
source("ooAugment.R")
source("ooFunctions.R")

rdatas = read.table(paste(fold,"/", name, "_rawDatas.txt",sep=""), header=T, sep="\t")
meandatas = read.table(paste(fold,"/", name, "_meanDatas.txt",sep=""), header=T, sep="\t")
whos = read.table(paste(fold,"/", name, "_whos.txt",sep=""), header=T, sep="\t")

rdatas = alignTime(rdatas)
rdatas$Time = rdatas$TimeToGP

naug = 3
write = 0
plotFeatImp = 1
savePred = 0
nfold = 10
balanced = 0

dt = 0.1
dot=-0.1
doScale = 0

library(randomForest) 
print( paste("proba ", sum(meandatas$Type==1)/length(meandatas$Type), sep="") )

nums = meandatas$Num
scorenums = rep(-1, length(nums))

eachres = c()
eachgt = c()
allres = c()
allresboth = c()

for ( t in dot )
{
	print(paste("Time ", t, sep=""))
	tmin = t
	tmax = tmin+dt

	# Select subset of uncorrelated features
	if (doScale) {
		subscale = scaleSubset( rdatas, tmin, tmax, 0, NULL, 1)
		subdatas = as.data.frame(subscale[[1]])
	 } else {
		 subdatas = projData(rdatas, tmin, tmax)
	}
	subset = subsetCluster( subdatas, 1)	

	#Randomly shuffle the data
	rnums = nums[sample(length(nums))]
	#Create equally size folds
	folds <- cut(seq(1,length(rnums)),breaks=nfold,labels=FALSE)
	#Perform 10 fold cross validation
	predictors = list()
	restest = c()

	for(i in 1:nfold) {
		#Separate data by fold using the which() function 
		testIndexes <- which(folds==i,arr.ind=TRUE)
		testnums = rnums[testIndexes]

		## Training
		trainData = rdatas[ !rdatas$Num%in%testnums,]
		## scale datas
		if (doScale){
		scalons = scaleSubset( trainData, tmin, tmax, 0, NULL, 1)
		tdatas = as.data.frame(scalons[[1]])
		scale = as.data.frame(scalons[[2]])
		} else {
			tdatas = projData(trainData, tmin, tmax)
		}

		if (savePred){
			if (doScale){
		saveRDS(scale, file=paste(fold, "/predictors/predictorK", "_scaling", "_bal", balanced, "_aug", naug, "_t", tmin, ".Rds", sep="") )
		}
		saveRDS(subset, file=paste(fold, "/predictors/predictorK", "_subset", "_bal", balanced, "_aug", naug, "_t", tmin, ".Rds", sep="") )
		}

		## subset selection
		tokeep = names(tdatas) %in% names(subset)
		subkeep = tdatas[,tokeep]
		types = tdatas$Type
		## RF
		wdatas = cbind(subkeep, types)
		names(wdatas)[length(names(wdatas))] = "Type"

		## SMOTE	
		if (naug>0) {
			for ( aug in 1:(naug) ) {
				auged = smote( wdatas[wdatas$Type==1,], 20 )
				dauged = data.frame(auged)
				colnames(dauged) = colnames(wdatas)
				rownames(dauged) = rep(length(wdatas[,1]), length(dauged[,1])) + 1:length(dauged[,1])
				wdatas = rbind( wdatas, dauged)
				
				auged = smote( wdatas[wdatas$Type==2,], 20 )
				dauged = data.frame(auged)
				colnames(dauged) = colnames(wdatas)
				rownames(dauged) = rep(length(wdatas[,1]), length(dauged[,1])) + 1:length(dauged[,1])
				wdatas = rbind( wdatas, dauged)
			}
		}	

		## Do RandomForest
			predictor = randomForest( as.factor(Type)~., data=wdatas, ntree=1000, na.action=na.roughfix )
			predictors[[i]] = predictor


		if (savePred){
			if (balanced<=1) save(predictor, file=paste(fold, "/predictors/predictorK", "_bal", balanced, "_aug", naug, "_t", tmin, ".RData", sep="") )
			if (balanced==2) {
			 save(predictor, file=paste(fold, "/predictors/predictorK", "_bal", 0, "_aug", naug, "_t", tmin, ".RData", sep="") )
			 save(predictorBal, file=paste(fold, "/predictors/predictorK", "_bal", 1, "_aug", naug, "_t", tmin, ".RData", sep="") )
			}
		}

		#### Test
		testData = rdatas[ rdatas$Num%in%testnums,]
		## scale
		if (doScale) {
		scalons = scaleSubset( testData, tmin, tmax, 1, scale, 1)
		trdatas = as.data.frame(scalons[[1]])
		} else {
			trdatas = projData(testData, tmin, tmax)
		}
		## subset
		gt = trdatas$Type
		t0num = trdatas$Num
		ckeep = names(trdatas) %in% names(subset)
		trdatas = trdatas[,ckeep]
		
		res = predict(predictor, trdatas)
		testscore =  sum(res==gt)/length(res) 
			
		print( testscore )
		restest = c(restest, sum(res==gt)/length(res) ) 
		eachgt = c(eachgt, factor(gt,levels=c(1,2)))
		eachres = c(eachres, factor(res,levels=c(1,2)))
		if (sum(gt==1)>0) {tpr = sum(res==1&gt==1)/sum(gt==1)}
		else {tpr=0}
		if (sum(gt==2)>0){tnr = sum(res==2&gt==2)/sum(gt==2)}
		else{tnr=0}
		bscore = (tpr+tnr)/2
		prec = sum(res==1&gt==1)/sum(res==1)
		rec = sum(res==1&gt==1)/sum(gt==1)
			
		allres = rbind(allres, c(((tmin+tmax)/2.0), testscore, bscore, prec, rec))

		## get scores
		resprob = predict(predictor, trdatas, type="prob")
			
			for ( k in 1:length(t0num) )
			{
				cnum = t0num[k]
				if ( scorenums[nums==cnum] == -1 ){ scorenums[nums==cnum] = resprob[k,2] } 
				else{ scorenums[nums==cnum] = scorenums[nums==cnum] + resprob[k,2] }
			}

	}
}

print(paste("Final ", sum(eachgt==eachres)/length(eachgt), sep="") )
tpr = sum(eachres==1&eachgt==1)/sum(eachgt==1)
print(paste("TPR (class0) ",tpr, sep="") ) 
tnr = sum(eachres==2&eachgt==2)/sum(eachgt==2)
print(paste("TNR (class1) ", tnr, sep="") ) 
print(paste("bACC ", (tnr+tpr)/2, sep="") ) 
print(paste("Precision ", sum(eachgt==1&eachres==1)/sum(eachres==1), sep="") ) 
print(paste("Recall ", sum(eachgt==1&eachres==1)/sum(eachgt==1), sep="") ) 

allres = as.data.frame(allres)
names(allres)=c("Time", "Accuracy", "balancedAccuracy", "Precision", "Recall")
if (write ) { write.table( allres, paste(fold, "/Scores_", name, ".txt", sep=""), col.names=T, row.names=F) }


### Feature importances for RF
imps = rep(0, length(names(rdatas) ) )
for ( k in 1:length(names(rdatas) )) 
{
		feat = (names(rdatas))[k]
		for ( j in 1:nfold )
		{
			foret = predictors[[j]]
			featnames = row.names(foret$importance)
			if ( sum(feat==featnames)>0 )
			{
				ind = which( featnames==feat )
				curimp = foret$importance[ind]
				imps[k] = imps[k] + curimp
			}
		}
}		

prims = order( imps, decreasing=T)
	#print( (names(rdatas))[prims] )

featscores = imps[prims]
featnames = (names(rdatas))[prims]

featnames = featnames[featscores>0]
featscores = featscores[featscores>0]
	

tmid = (tmin+tmax)/2
if ( plotFeatImp == 1 )
{
	featscores = rev(featscores)
	featnames=rev(featnames)
		png(paste(fold, "/RF_featureImportance_t", tmid, ".png", sep=""))
		par(mar=c(4,8,2,1) )
		barplot( featscores, names.arg=featnames, horiz=T, las=1, xlab="Feature importance", cex.names=0.5)
		dev.off()
	df = cbind(featnames, featscores)
	names(df) = c("Feature", "FeatureImportance")
		write.table(df, paste(fold, "/RF_featureImportance_t", tmid, "_values.csv", sep=""), sep=",", col.names=T, row.names=F)
}
df = cbind( scorenums, meandatas$Num )
df = data.frame(df)
names(df) = c("PredictionScore", "Num")

#ntable = read.table( paste(fold, "/", name, "_names.txt", sep=""), header=T )
#names(ntable) = c("Num", "Name")
#qual = merge(df, ntable, by="Num")
#mod = lm( Expulsion~., data=wdatas)
#bmod = glm(Expulsion~., data=wdatas, family="binomial")
#write.table(df, paste(fold, "/incompscore.txt", sep=""), row.names=F, col.names=T)
