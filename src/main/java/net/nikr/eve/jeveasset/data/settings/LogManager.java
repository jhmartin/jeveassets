/*
 * Copyright 2009-2017 Contributors (see credits.txt)
 *
 * This file is part of jEveAssets.
 *
 * jEveAssets is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * jEveAssets is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jEveAssets; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */
package net.nikr.eve.jeveasset.data.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.nikr.eve.jeveasset.data.api.my.MyAsset;
import net.nikr.eve.jeveasset.data.api.my.MyContractItem;
import net.nikr.eve.jeveasset.data.api.my.MyIndustryJob;
import net.nikr.eve.jeveasset.data.api.my.MyJournal;
import net.nikr.eve.jeveasset.data.api.my.MyMarketOrder;
import net.nikr.eve.jeveasset.data.api.my.MyTransaction;
import net.nikr.eve.jeveasset.data.api.raw.RawJournalRefType;
import net.nikr.eve.jeveasset.data.profile.ProfileData;
import net.nikr.eve.jeveasset.data.sde.Item;
import net.nikr.eve.jeveasset.gui.tabs.log.LogChangeType;
import net.nikr.eve.jeveasset.gui.tabs.log.LogType;
import net.nikr.eve.jeveasset.gui.tabs.log.MyLog;
import net.nikr.eve.jeveasset.gui.tabs.log.RawLog;
import net.nikr.eve.jeveasset.gui.tabs.log.RawLog.LogData;
import net.nikr.eve.jeveasset.i18n.General;
import net.nikr.eve.jeveasset.io.local.LogsReader;
import net.nikr.eve.jeveasset.io.local.LogsWriter;


public class LogManager {
	private static Map<Date, Set<RawLog>> logs =  null;

	private LogManager() { }

	public static synchronized Set<MyLog> getList() {
		Set<RawLog> logSet = new HashSet<>();
		for (Set<RawLog> log : getLogs().values()) {
			logSet.addAll(log);
		}
		return Collections.unmodifiableSet(convert(logSet));
	}

	private static void add(Date date, Set<RawLog> c) {
		getLogs().put(date, c);
		save();
	}

	private static void save() {
		LogsWriter.save(logs);
	}

	private static Set<MyLog> convert(Collection<RawLog> logs) {
		Set<MyLog> myLogs = new HashSet<MyLog>();
		Settings.lock("Creating Log");
		for (RawLog rawLog : logs) {
			myLogs.add(new MyLog(rawLog));
		}
		Settings.unlock("Creating Log");
		return myLogs;
	}

	private static Map<Date, Set<RawLog>> getLogs() {
		if (logs == null) {
			logs = new HashMap<Date, Set<RawLog>>();
			LogsReader.load(logs);
		}
		return logs;
	}

	private static Date getStartDate() {
		Iterator<Date> iterator = getLogs().keySet().iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		} else {
			return null;
		}
	}

	public static void createLog(List<MyAsset> oldAssets, Date start, ProfileData profileData) {
		if (oldAssets.isEmpty()) {
			return;
		}
		List<MyAsset> newAssets = profileData.getAssetsList();
		Date end = new Date();
		Map<Long, LogAssetType> oldMap = new HashMap<Long, LogAssetType>();
		Map<Long, LogAssetType> newMap = new HashMap<Long, LogAssetType>();
		for (MyAsset asset : oldAssets) {
			if (asset.isGenerated()) {
				continue;
			}
			if (asset.getFlag().equals(General.get().industryJobFlag())) {
				continue;
			}
			oldMap.put(asset.getItemID(), asset);
		}
		for (MyAsset asset : newAssets) {
			if (asset.isGenerated()) {
				continue;
			}
			if (asset.getFlag().equals(General.get().industryJobFlag())) {
				continue;
			}
			newMap.put(asset.getItemID(), asset);
		}
		Set<RawLog> newLogs = new HashSet<RawLog>();
	//New
		//Added Assets
		Map<Long, LogAssetType> added = new HashMap<Long, LogAssetType>(newMap); //New Assets
		added.keySet().removeAll(oldMap.keySet()); //Removed Old Assets
		List<LogAssetType> unknownAdded = new ArrayList<>();
		added(profileData, start, end, unknownAdded, newLogs, added.values());

		//Removed Assets
		Map<Long, LogAssetType> removed = new HashMap<Long, LogAssetType>(oldMap); //Old Assets
		removed.keySet().removeAll(newMap.keySet()); //Remove New Assets
		List<LogAssetType> unknownRemoved = new ArrayList<>();
		removed(profileData, start, end, unknownRemoved, newLogs, removed.values());

		//Moved
		Map<Long, LogAssetType> same = new HashMap<Long, LogAssetType>(oldMap); //Old Assets
		same.keySet().retainAll(newMap.keySet()); //Assets in both New and Old (retain)
		//Moved: Same itemID
		for (Long itemID : same.keySet()) {
			LogAssetType oldAsset = oldMap.get(itemID);
			LogAssetType newAsset = newMap.get(itemID);
			List<Long> oldParents = new ArrayList<>();
			List<Long> newParents = new ArrayList<>();
			for (LogAssetType asset : oldAsset.getParents()) {
				oldParents.add(asset.getItemID());
			}
			for (LogAssetType asset : newAsset.getParents()) {
				newParents.add(asset.getItemID());
			}
			boolean owner = oldAsset.getOwnerID() != newAsset.getOwnerID(); //New Owner
			boolean location = oldAsset.getLocationID() != newAsset.getLocationID();  //New Location
			boolean flag =  !oldAsset.getFlagID().equals(newAsset.getFlagID()); //New Flag
			boolean container = !oldParents.equals(newParents); //New Container
			if (location || flag || container || owner) {
				LogData oldData = new LogData(oldAsset);
				LogData newData = new LogData(newAsset);
				newLogs.add(new RawLog(end, oldAsset.getItemID(), oldAsset.getTypeID(), oldData, newData, LogData.changed(end, oldData, newData, 100)));
			}
			if (oldAsset.getCount() > newAsset.getCount()) {
				unknownRemoved.add(new LogAsset(newAsset, oldAsset.getCount() - newAsset.getCount()));
			}
		}
		//Moved: new itemID
		boolean loot = canBeLoot(start, end, profileData.getJournalList());
		moved(end, newLogs, unknownAdded, unknownRemoved, loot);

		add(end, newLogs);
	}

	private static void moved(Date end, Set<RawLog> newLogs, Collection<LogAssetType> added, Collection<LogAssetType> removed, boolean loot) {
		//Add Claims
		Set<Integer> typeIDs = new HashSet<>();
		Map<Integer, List<ClaimAsset>> claims = new HashMap<>();
		for (LogAssetType asset : added) {
			typeIDs.add(asset.getTypeID());
			put(claims, asset.getTypeID(), new ClaimAsset(asset, asset.getOwnerID(), asset.getLocationID(), asset.getTypeID(), asset.getCount()));
		}
		//Add Sources
		Map<Integer, List<SourceAsset>> sources = new HashMap<>();
		for (LogAssetType asset : removed) {
			int typeID = asset.getTypeID();
			if (!typeIDs.contains(typeID)) { //TypeID does not match - Remain Unknown
				continue;
			}
			put(sources, typeID, new SourceAsset(asset, end));
		}
		//Resolve claims
		for (Map.Entry<Integer, List<ClaimAsset>> entry : claims.entrySet()) {
			List<SourceAsset> soruceList = sources.get(entry.getKey());
			if (soruceList == null) {
				continue;
			}
			for (Claim claim : entry.getValue()) {
				for (Source source : soruceList) {
					source.addClaim(claim);
				}
			}
			for (Source source : soruceList) {
				source.claim();
			}
		}
		for (List<ClaimAsset> list : claims.values()) {
			for (ClaimAsset claim : list) {
				Map<LogChangeType, Set<LogType>> logTypes = new HashMap<>();
				LogAssetType fromAsset = null;
				for (LogTypeAsset logTypeAsset : claim.getSourceAssets()) {
					removed.remove(logTypeAsset.getAsset()); //Remove Assets that was moved
					logTypes.putAll(LogData.changed(end, new LogData(logTypeAsset.getAsset()), new LogData(claim.getAsset()), logTypeAsset.getPercent()));
					fromAsset = logTypeAsset.getAsset();
				}
				if (logTypes.isEmpty()) {
					if (loot && (claim.getAsset().getItem().getCategory().equals("Drone") 
							|| claim.getAsset().getItem().getCategory().equals("Commodity")
							|| claim.getAsset().getItem().getCategory().equals("Module")
							|| claim.getAsset().getItem().getCategory().equals("Charge"))) {
						logTypes.put(LogChangeType.ADDED_LOOT, Collections.singleton(new LogType(end, LogChangeType.ADDED_LOOT, 25)));
					} else {
						logTypes.put(LogChangeType.ADDED_UNKNOWN, Collections.singleton(new LogType(end, LogChangeType.ADDED_UNKNOWN, 0)));
					}
				}
				newLogs.add(new RawLog(end, claim.getAsset().getItemID(), claim.getAsset().getTypeID(), fromAsset == null ? null : new LogData(fromAsset), new LogData(claim.getAsset()), logTypes));
			}
		}
		for (LogAssetType asset : removed) {
			Map<LogChangeType, Set<LogType>> logTypes = Collections.singletonMap(LogChangeType.REMOVED_UNKNOWN, Collections.singleton(new LogType(end, LogChangeType.REMOVED_UNKNOWN, 0)));
			newLogs.add(new RawLog(end, asset.getItemID(), asset.getTypeID(), new LogData(asset), null, logTypes));
		}
	}

	private static void added(ProfileData profileData, Date start, Date end, List<LogAssetType> unknown, Set<RawLog> newLogs, Collection<LogAssetType> added) {
		//Add Claims
		Set<Integer> typeIDs = new HashSet<>();
		Map<Integer, List<Claim>> claims = new HashMap<>();
		for (LogAssetType asset : added) {
			typeIDs.add(asset.getTypeID());
			put(claims, asset.getTypeID(), new Claim(asset, asset.getOwnerID(), asset.getLocationID(), asset.getTypeID(), asset.getCount()));
		}
		//Add Sources
		Map<Integer, List<Source>> sources = new HashMap<>();
		addedTransactionsBought(sources, start, end, profileData.getTransactionsList(), typeIDs);
		addedContractAccepted(sources, start, end, profileData.getContractItemList(), typeIDs);
		addedIndustryJobsDelivered(sources, start, end, profileData.getIndustryJobsList(), typeIDs);
		//Resolve claims
		for (Map.Entry<Integer, List<Claim>> entry : claims.entrySet()) {
			List<Source> soruceList = sources.get(entry.getKey());
			if (soruceList == null) {
				continue;
			}
			for (Claim claim : entry.getValue()) {
				for (Source source : soruceList) {
					source.addClaim(claim);
				}
			}
			for (Source source : soruceList) {
				source.claim();
			}
		}
		for (List<Claim> list : claims.values()) {
			for (Claim claim : list) {
				if (claim.getNeed() > 0) {
					unknown.add(claim.getAsset());
					continue;
				}
				Map<LogChangeType, Set<LogType>> logTypes = claim.getLogType();
				if (logTypes.isEmpty()) {
					unknown.add(claim.getAsset());
					continue;
				}
				newLogs.add(new RawLog(end, claim.getAsset().getItemID(), claim.getAsset().getTypeID(), null, new LogData(claim.getAsset()), logTypes));
			}
		}
	}

	private static void removed(ProfileData profileData, Date start, Date end, List<LogAssetType> unknown, Set<RawLog> newLogs, Collection<LogAssetType> removed) {
		//Add Claims
		Set<Integer> typeIDs = new HashSet<>();
		Map<Integer, List<Claim>> claims = new HashMap<>();
		for (LogAssetType asset : removed) {
			typeIDs.add(asset.getTypeID());
			put(claims, asset.getTypeID(), new Claim(asset, asset.getOwnerID(), asset.getLocationID(), asset.getTypeID(), asset.getCount()));
		}
		//Add Sources
		Map<Integer, List<Source>> removedSources = new HashMap<>();
		removedSellMarketOrderCreated(removedSources, start, end, profileData.getMarketOrdersList(), typeIDs);
		removedContractCreated(removedSources, start, end, profileData.getContractItemList(), typeIDs);
		removedContractAccepted(removedSources, start, end, profileData.getContractItemList(), typeIDs);
		removedIndustryJobsCreated(removedSources, start, end, profileData.getIndustryJobsList(), typeIDs);
		//Resolve claims
		for (Map.Entry<Integer, List<Claim>> entry : claims.entrySet()) {
			List<Source> soruceList = removedSources.get(entry.getKey());
			if (soruceList == null) {
				continue;
			}
			for (Claim claim : entry.getValue()) {
				for (Source source : soruceList) {
					source.addClaim(claim);
				}
			}
			for (Source source : soruceList) {
				source.claim();
			}
		}
		//Create Logs from Claims
		for (List<Claim> list : claims.values()) {
			for (Claim claim : list) {
				if (claim.getNeed() > 0) {
					unknown.add(claim.getAsset());
					continue;
				}
				Map<LogChangeType, Set<LogType>> logTypes = claim.getLogType();
				if (logTypes.isEmpty()) {
					unknown.add(claim.getAsset());
					continue;
				}
				newLogs.add(new RawLog(end, claim.getAsset().getItemID(), claim.getAsset().getTypeID(), new LogData(claim.getAsset()), null, logTypes));
			}
		}
	}

	private static <K, V> void put(Map<K, List<V>> map, K k, V v) {
		List<V> list = map.get(k);
		if (list == null) {
			list = new ArrayList<V>();
			map.put(k, list);
		}
		list.add(v);
	}

	public static <K, V> void putSet(Map<K, Set<V>> map, K k, V v) {
		Set<V> list = map.get(k);
		if (list == null) {
			list = new HashSet<V>();
			map.put(k, list);
		}
		list.add(v);
	}

	private static void removedSellMarketOrderCreated(Map<Integer, List<Source>> sources, Date start, Date end, List<MyMarketOrder> marketOrders, Set<Integer> typeIDs) {
		for (MyMarketOrder marketOrder : marketOrders) {
			Date date = marketOrder.getIssued();
			int typeID = marketOrder.getTypeID();
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (marketOrder.isBuyOrder()) { //Ignore Buy Orders
				continue;
			}
			if (!typeIDs.contains(typeID)) { //TypeID does not match
				continue;
			}
			long ownerID = marketOrder.getOwnerID();
			long locationID = marketOrder.getLocationID();
			int quantity = marketOrder.getVolEntered();
			LogChangeType changeType = LogChangeType.REMOVED_MARKET_ORDER_CREATED;
			put(sources, typeID, new Source(ownerID, locationID, typeID, quantity, date, changeType));
		}
	}

	private static void removedContractCreated(Map<Integer, List<Source>> sources, Date start, Date end, List<MyContractItem> contractItems, Set<Integer> typeIDs) {
		for (MyContractItem contractItem : contractItems) {
			Date date = contractItem.getContract().getDateIssued();
			int typeID = contractItem.getTypeID();
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (contractItem.getContract().isIgnoreContract()) { //Wrong contract type
				continue;
			}
			if (!contractItem.isIncluded()) { //Ignore items being bought
				continue;
			}
			if (!typeIDs.contains(typeID)) { //TypeID does not match
				continue;
			}
			long ownerID = contractItem.getContract().getIssuerID();
			long locationID = contractItem.getContract().getStartLocationID();
			int quantity = contractItem.getQuantity();
			LogChangeType changeType = LogChangeType.REMOVED_CONTRACT_CREATED;
			put(sources, typeID, new Source(ownerID, locationID, typeID, quantity, date, changeType));
		}
	}

	private static void removedContractAccepted(Map<Integer, List<Source>> sources, Date start, Date end, List<MyContractItem> contractItems, Set<Integer> typeIDs) {
		for (MyContractItem contractItem : contractItems) {
			Date date = contractItem.getContract().getDateCompleted();
			int typeID = contractItem.getTypeID();
			if (date == null) { //Not Completed
				continue;
			}
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (contractItem.getContract().isIgnoreContract()) { //Wrong contract type
				continue;
			}
			if (contractItem.isIncluded()) { //Ignore items bought
				continue;
			}
			if (!typeIDs.contains(typeID)) { //TypeID does not match
				continue;
			}
			long ownerID = contractItem.getContract().getAcceptorID();
			long locationID = contractItem.getContract().getStartLocationID();
			int quantity = contractItem.getQuantity();
			LogChangeType changeType = LogChangeType.REMOVED_CONTRACT_ACCEPTED;
			put(sources, typeID, new Source(ownerID, locationID, typeID, quantity, date, changeType));
		}
	}

	private static void removedIndustryJobsCreated(Map<Integer, List<Source>> sources, Date start, Date end, List<MyIndustryJob> industryJobs, Set<Integer> typeIDs) {
		for (MyIndustryJob industryJob : industryJobs) {
			Date date = industryJob.getStartDate();
			int typeID = industryJob.getBlueprintTypeID();
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (industryJob.isDelivered()) { //Ignore delivered jobs
				continue;
			}
			if (!typeIDs.contains(typeID)) { //TypeID does not match
				continue;
			}
			long ownerID = industryJob.getOwnerID();
			long locationID = industryJob.getBlueprintLocationID();
			int quantity = 1;
			LogChangeType changeType = LogChangeType.REMOVED_INDUSTRY_JOB_CREATED;
			put(sources, typeID, new Source(ownerID, locationID, typeID, quantity, date, changeType));
		}
	}

	private static void addedTransactionsBought(Map<Integer, List<Source>> sources, Date start, Date end, List<MyTransaction> transactions, Set<Integer> typeIDs) {
		for (MyTransaction transaction : transactions) {
			Date date = transaction.getDate();
			int typeID = transaction.getTypeID();
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (transaction.isSell()) { //Ignore Sell Transactions
				continue;
			}
			if (!typeIDs.contains(typeID)) { //TypeID does not match
				continue;
			}
			long ownerID = transaction.getOwnerID();
			long locationID = transaction.getLocationID();
			int quantity = transaction.getQuantity();
			LogChangeType changeType = LogChangeType.ADDED_TRANSACTIONS_BOUGHT;
			put(sources, typeID, new Source(ownerID, locationID, typeID, quantity, date, changeType));
		}
	}
	private static boolean canBeLoot(Date start, Date end, List<MyJournal> journals) {
		for (MyJournal journal : journals) {
			Date date = journal.getDate();
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (journal.getRefType() == RawJournalRefType.BOUNTY_PRIZES || journal.getRefType() == RawJournalRefType.BOUNTY_PRIZE) {
				return true;
			}
		}
		return false;
	}

	private static void addedContractAccepted(Map<Integer, List<Source>> sources, Date start, Date end, List<MyContractItem> contractItems, Set<Integer> typeIDs) {
		for (MyContractItem contractItem : contractItems) {
			Date date = contractItem.getContract().getDateCompleted();
			int typeID = contractItem.getTypeID();
			if (date == null) { //Not Completed
				continue;
			}
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (contractItem.getContract().isIgnoreContract()) { //Wrong contract type
				continue;
			}
			if (!contractItem.isIncluded()) { //Ignore sold items
				continue;
			}
			if (!typeIDs.contains(typeID)) { //TypeID does not match
				continue;
			}
			long ownerID = contractItem.getContract().getAcceptorID();
			long locationID = contractItem.getContract().getStartLocationID();
			int quantity = contractItem.getQuantity();
			LogChangeType changeType = LogChangeType.ADDED_CONTRACT_ACCEPTED;
			put(sources, typeID, new Source(ownerID, locationID, typeID, quantity, date, changeType));
		}
	}

	private static void addedIndustryJobsDelivered(Map<Integer, List<Source>> sources, Date start, Date end, List<MyIndustryJob> industryJobs, Set<Integer> typeIDs) {
		for (MyIndustryJob industryJob : industryJobs) {
			Date date = industryJob.getCompletedDate();
			int blueprintTypeID = industryJob.getBlueprintTypeID();
			int productTypeID = industryJob.getProductTypeID();
			if (date == null) { //Not completed yet
				continue;
			}
			if (start != null && date.before(start)) { //Outside Date range
				continue;
			}
			if (date.after(end)) { //Outside Date range
				continue;
			}
			if (!industryJob.isDelivered()) { //Not delivered AKA not in assets yet
				continue;
			}
			if (!typeIDs.contains(blueprintTypeID) && !typeIDs.contains(productTypeID)) { //TypeID does not match
				continue;
			}
			long ownerID = industryJob.getOwnerID();
			long blueprintLocationID = industryJob.getBlueprintLocationID();
			int blueprintQuantity = 1;
			LogChangeType changeType = LogChangeType.ADDED_INDUSTRY_JOB_DELIVERED;
			put(sources, blueprintTypeID, new Source(ownerID, blueprintLocationID, blueprintTypeID, blueprintQuantity, date, changeType));
			if (industryJob.isManufacturing() && industryJob.isCompleted()) {
				long productLocationID = industryJob.getOutputLocationID();
				int productQuantity = industryJob.getOutputCount();
				put(sources, productTypeID, new Source(ownerID, productLocationID, productTypeID, productQuantity, date, changeType));
			}
		}
	}

	private static class LogTypeAsset {
		private final LogAssetType asset;
		private final int percent;

		public LogTypeAsset(LogAssetType asset, int percent) {
			this.asset = asset;
			this.percent = percent;
		}

		public LogAssetType getAsset() {
			return asset;
		}

		public int getPercent() {
			return percent;
		}
	}

	private static class ClaimAsset extends Claim {

		private final List<LogTypeAsset> sourceAssets = new ArrayList<>();

		public ClaimAsset(LogAssetType asset, long ownerID, long locationID, int typeID, Long quantity) {
			super(asset, ownerID, locationID, typeID, quantity);
		}

		@Override
		public void addCount(Source source, int percent, int count) {
			super.addCount(source, percent, count);
			sourceAssets.add(new LogTypeAsset(((SourceAsset)source).getAsset(), percent));
		}

		public List<LogTypeAsset> getSourceAssets() {
			return sourceAssets;
		}
	}
	private static class Claim implements Comparable<Claim> {
		private final LogAssetType asset;
		private final long ownerID;
		private final long locationID;
		private final int typeID;
		private final int quantity;
		private int needed;
		private int available = 0;
		private final Map<LogChangeType, Set<LogType>> logType = new HashMap<>();

		public Claim(LogAssetType asset, long ownerID, long locationID, int typeID, Long quantity) {
			this.asset = asset;
			this.ownerID = ownerID;
			this.locationID = locationID;
			this.typeID = typeID;
			this.quantity = quantity.intValue();
			this.needed = quantity.intValue();
		}

		public LogAssetType getAsset() {
			return asset;
		}

		public long getOwnerID() {
			return ownerID;
		}

		public long getLocationID() {
			return locationID;
		}

		public int getTypeID() {
			return typeID;
		}

		public int getQuantity() {
			return quantity;
		}

		public Map<LogChangeType, Set<LogType>> getLogType() {
			return logType;
		}

		public void addAvailable(int available) {
			this.available =+ available;
		}

		public void addCount(Source source, int percent, int count) {
			putSet(logType, source.getChangeType(), new LogType(source.getDate(), source.getChangeType(), percent));
			needed = needed - count;
		}

		private int getNeed() { //Claim optimization
			return available - needed;
		}

		@Override
		public int compareTo(Claim o) {
			if (this.getNeed() > o.getNeed()) {
				return 1;
			} else if  (this.getNeed() < o.getNeed()){
				return -1;
			} else {
				return 0;
			}
		}
	}

	private static class SourceAsset extends Source {

		private final LogAssetType asset;
		public SourceAsset(LogAssetType asset, Date date) {
			super(asset.getOwnerID(), asset.getLocationID(), asset.getTypeID(), (int) asset.getCount(), date, LogChangeType.MOVED_UNKNOWN);
			this.asset = asset;
		}

		public LogAssetType getAsset() {
			return asset;
		}
	}
	private static class Source  {
		private final long ownerID;
		private final long locationID;
		private final int typeID;
		private final int quantity;
		private final Date date;
		private final LogChangeType changeType;
		private final Map<Match, List<Claim>> claims = new TreeMap<>();

		public Source(long ownerID, long locationID, int typeID, int quantity, Date date, LogChangeType changeType) {
			this.ownerID = ownerID;
			this.locationID = locationID;
			this.typeID = typeID;
			this.quantity = quantity;
			this.date = date;
			this.changeType = changeType;
		}

		public long getOwnerID() {
			return ownerID;
		}

		public long getLocationID() {
			return locationID;
		}

		public int getTypeID() {
			return typeID;
		}

		public int getQuantity() {
			return quantity;
		}

		public Date getDate() {
			return date;
		}

		public LogChangeType getChangeType() {
			return changeType;
		}

		private int match(Claim claim) {
			int match = 25; //Match TypeID
			if (claim.getLocationID() == getLocationID()) {
				match = match + 50;
			}
			if (claim.getQuantity()== getQuantity()) {
				match = match + 25;
			}
			if (match == 100) {
				match--; //99% is max
			}
			return match;
		}

		public void addClaim(Claim claim) {
			if (claim.getOwnerID() != getOwnerID()) {
				return; //Wrong owner
			}
			Match match = new Match(match(claim), claim.getQuantity());
			List<Claim> claimList = claims.get(match);
			if (claimList == null) {
				claimList = new ArrayList<Claim>();
				claims.put(match, claimList);
			}
			claimList.add(claim);
			claim.addAvailable(match.getCount());
		}

		public void claim() {
			for (Map.Entry<Match, List<Claim>> entry : claims.entrySet()) {
				List<Claim> claimList = entry.getValue();
				Match match = entry.getKey();
				Collections.sort(claimList, new ClaimComparator(match.getCount())); //Sort by need
				for (Claim claim : claimList) {
					if (claim.getNeed() >= match.getCount()) { //Add all
						claim.addCount(this, match.getPercent(), match.getCount());
						match.takeAll();
						break;
					} else { //Add part of the count
						int missing = claim.getNeed();
						claim.addCount(this, match.getPercent(), missing);
						match.take(missing);
					}
				}
			}
		}
	}

	private static class ClaimComparator implements Comparator<Claim> {

		private final int target;

		public ClaimComparator(int target) {
			this.target = target;
		}
		
		@Override
		public int compare(Claim o1, Claim o2) {
			int t1 = Math.abs(o1.getNeed() - target); //Distance from target
			int t2 = Math.abs(o2.getNeed() - target); //Distance from target
			if (t1 > t2) {
				return 1;
			} else if  (t1 < t2){
				return -1;
			} else {
				return 0;
			}
		}
		
	}
	public static class Match implements Comparable<Match> {
		private final int percent;
		private int count;

		public Match(int percent, int count) {
			this.percent = percent;
			this.count = count;
		}

		public void takeAll() {
			count = 0;
		}

		public void take(int missing) {
			count = count - missing;
		}

		public int getCount() {
			return count;
		}

		public int getPercent() {
			return percent;
		}

		@Override
		public int compareTo(Match o) {
			return o.percent - this.percent;
		}

		@Override
		public int hashCode() {
			int hash = 3;
			hash = 83 * hash + this.percent;
			return hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final Match other = (Match) obj;
			if (this.percent != other.percent) {
				return false;
			}
			return true;
		}
	}

	public static interface LogAssetType {
		public long getOwnerID();
		public long getLocationID();
		public Integer getTypeID();
		public long getCount();
		public Long getItemID();
		public Integer getFlagID();
		public String getContainer();
		public List<MyAsset> getParents();
		public Item getItem();
	}

	private static class LogAsset implements LogAssetType {
		private final long ownerID;
		private final long locationID;
		private final Integer typeID;
		private final long count;
		private final Long itemID;
		private final Integer flagID;
		private final String container = "";
		private final List<MyAsset> parents;
		private final Item item;

		public LogAsset(LogAssetType asset, long count) {
			this.ownerID = asset.getOwnerID();
			this.locationID = asset.getLocationID();
			this.typeID = asset.getTypeID();
			this.count = count;
			this.itemID = asset.getItemID();
			this.flagID = asset.getFlagID();
			this.parents = asset.getParents();
			this.item = asset.getItem();
		}

		@Override
		public long getOwnerID() {
			return ownerID;
		}

		@Override
		public long getLocationID() {
			return locationID;
		}

		@Override
		public Integer getTypeID() {
			return typeID;
		}

		@Override
		public long getCount() {
			return count;
		}

		@Override
		public Long getItemID() {
			return itemID;
		}

		@Override
		public Integer getFlagID() {
			return flagID;
		}

		@Override
		public String getContainer() {
			return container;
		}

		@Override
		public List<MyAsset> getParents() {
			return parents;
		}

		@Override
		public Item getItem() {
			return item;
		}
	}
}