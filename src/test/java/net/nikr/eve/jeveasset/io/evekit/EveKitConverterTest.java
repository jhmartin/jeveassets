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
package net.nikr.eve.jeveasset.io.evekit;

import static org.junit.Assert.assertEquals;

import enterprises.orbital.evekit.client.model.AccountBalance;
import enterprises.orbital.evekit.client.model.Asset;
import enterprises.orbital.evekit.client.model.Blueprint;
import enterprises.orbital.evekit.client.model.Contract;
import enterprises.orbital.evekit.client.model.ContractItem;
import enterprises.orbital.evekit.client.model.IndustryJob;
import enterprises.orbital.evekit.client.model.MarketOrder;
import enterprises.orbital.evekit.client.model.WalletJournal;
import enterprises.orbital.evekit.client.model.WalletTransaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.nikr.eve.jeveasset.TestUtil;
import net.nikr.eve.jeveasset.data.MyAccountBalance;
import net.nikr.eve.jeveasset.data.raw.RawBlueprint;
import net.nikr.eve.jeveasset.gui.tabs.assets.MyAsset;
import net.nikr.eve.jeveasset.gui.tabs.contracts.MyContract;
import net.nikr.eve.jeveasset.gui.tabs.contracts.MyContractItem;
import net.nikr.eve.jeveasset.gui.tabs.jobs.MyIndustryJob;
import net.nikr.eve.jeveasset.gui.tabs.journal.MyJournal;
import net.nikr.eve.jeveasset.gui.tabs.orders.MyMarketOrder;
import net.nikr.eve.jeveasset.gui.tabs.transaction.MyTransaction;
import net.nikr.eve.jeveasset.io.shared.ConverterTestOptions;
import net.nikr.eve.jeveasset.io.shared.ConverterTestOptionsGetter;
import net.nikr.eve.jeveasset.io.shared.ConverterTestUtil;
import org.junit.Test;

public class EveKitConverterTest extends TestUtil {

	public EveKitConverterTest() {
	}

	@Test
	public void testToAccountBalance() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			AccountBalance accountBalance = new AccountBalance();
			ConverterTestUtil.setValues(accountBalance, options);
			List<MyAccountBalance> accountBalances = EveKitConverter.toAccountBalance(Collections.singletonList(accountBalance), ConverterTestUtil.getEveKitOwner(options));
			ConverterTestUtil.testValues(accountBalances.get(0), options);
		}
	}

	@Test
	public void testToAssets() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			List<Asset> assetsList = new ArrayList<Asset>();
			Asset rootAsset = new Asset();
			assetsList.add(rootAsset);
			ConverterTestUtil.setValues(rootAsset, options);

			Asset childAsset = new Asset();
			assetsList.add(childAsset);
			ConverterTestUtil.setValues(childAsset, options);
			childAsset.setItemID(childAsset.getItemID() + 1);
			childAsset.setLocationID(rootAsset.getItemID());

			List<MyAsset> assets = EveKitConverter.toAssets(assetsList, ConverterTestUtil.getEveKitOwner(options));
			if (rootAsset.getFlag() != 89) {
				assertEquals("List empty @" + options.getIndex(), 1, assets.size());
				ConverterTestUtil.testValues(assets.get(0), options);

				assertEquals("List empty @" + options.getIndex(), 1, assets.get(0).getAssets().size());
				MyAsset childMyAsset = assets.get(0).getAssets().get(0);
				childMyAsset.setItemID(childMyAsset.getItemID() - 1);
				ConverterTestUtil.testValues(childMyAsset, options);
			} else {
				assertEquals(assets.size(), 0);
			}
		}
	}

	@Test
	public void testToBlueprints() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			Blueprint blueprint = new Blueprint();
			ConverterTestUtil.setValues(blueprint, options);
			Map<Long, RawBlueprint> blueprints = EveKitConverter.toBlueprints(Collections.singletonList(blueprint));
			ConverterTestUtil.testValues(blueprints.values().iterator().next(), options);
		}
	}

	@Test
	public void testToContracts() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			Contract contract = new Contract();
			ConverterTestUtil.setValues(contract, options);
			Map<MyContract, List<MyContractItem>> contracts = EveKitConverter.toContracts(Collections.singletonList(contract), ConverterTestUtil.getEveKitOwner(options));
			ConverterTestUtil.testValues(contracts.keySet().iterator().next(), options);
		}
	}

	@Test
	public void testToContractItems() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			ContractItem contractItem = new ContractItem();
			ConverterTestUtil.setValues(contractItem, options);
			Map<MyContract, List<MyContractItem>> contractItems = EveKitConverter.toContractItems(ConverterTestUtil.getMyContract(false, false, options), Collections.singletonList(contractItem), ConverterTestUtil.getEveKitOwner(options));
			ConverterTestUtil.testValues(contractItems.values().iterator().next().get(0), options);
		}
	}

	@Test
	public void testToIndustryJobs() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			IndustryJob industryJob = new IndustryJob();
			ConverterTestUtil.setValues(industryJob, options);
			List<MyIndustryJob> industryJobs = EveKitConverter.toIndustryJobs(Collections.singletonList(industryJob), ConverterTestUtil.getEveKitOwner(options));
			ConverterTestUtil.testValues(industryJobs.get(0), options);
		}
	}

	@Test
	public void testToJournals() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			WalletJournal walletJournal = new WalletJournal();
			ConverterTestUtil.setValues(walletJournal, options);
			Set<MyJournal> journals = EveKitConverter.toJournals(Collections.singletonList(walletJournal), ConverterTestUtil.getEveKitOwner(options));
			ConverterTestUtil.testValues(journals.iterator().next(), options);
		}
	}

	@Test
	public void testToMarketOrders() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			MarketOrder marketOrder = new MarketOrder();
			ConverterTestUtil.setValues(marketOrder, options);
			List<MyMarketOrder> marketOrders = EveKitConverter.toMarketOrders(Collections.singletonList(marketOrder), ConverterTestUtil.getEveKitOwner(options));
			ConverterTestUtil.testValues(marketOrders.get(0), options);
		}
	}

	@Test
	public void testToTransactions() {
		for (ConverterTestOptions options : ConverterTestOptionsGetter.getConverterOptions()) {
			WalletTransaction transaction = new WalletTransaction();
			ConverterTestUtil.setValues(transaction, options);
			Set<MyTransaction> transactions = EveKitConverter.toTransactions(Collections.singletonList(transaction), ConverterTestUtil.getEveKitOwner(options));
			ConverterTestUtil.testValues(transactions.iterator().next(), options);
		}
	}
}