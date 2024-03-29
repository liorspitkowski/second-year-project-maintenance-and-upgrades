/***************************************************************************
 *                   (C) Copyright 2003-2016 - Stendhal                    *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.server.actions.query;

import static games.stendhal.common.constants.Actions.PROGRESS_STATUS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import games.stendhal.server.actions.ActionListener;
import games.stendhal.server.actions.CommandCenter;
import games.stendhal.server.core.engine.SingletonRepository;
import games.stendhal.server.core.rp.StendhalQuestSystem;
import games.stendhal.server.entity.player.Player;
import games.stendhal.server.entity.slot.BankStatement;
import games.stendhal.server.entity.slot.Banks;
import games.stendhal.server.events.ProgressStatusEvent;
import marauroa.common.game.RPAction;

/**
 * queries the server about the progress.
 *
 * @author hendrik
 */
public class ProgressStatusQueryAction implements ActionListener {


	/**
	 * registers this action.
	 */
	public static void register() {
		CommandCenter.register(PROGRESS_STATUS, new ProgressStatusQueryAction());
	}

	/**
	 * processes the requested action.
	 *
	 * @param player the caller of the action
	 * @param action the action to be performed
	 */
	@Override
	public void onAction(final Player player, final RPAction action) {
		if (!action.has("progress_type")) {
			sendProgressTypes(player);
		} else if (!action.has("item")) {
			sendItemList(player, action.get("progress_type"));
		} else {
			sendDetails(player, action.get("progress_type"), action.get("item"));
		}

		player.notifyWorldAboutChanges();
	}

	/**
	 * sends a list of supported progress types
	 *
	 * @param player Player to sent the event to
	 */
	private void sendProgressTypes(Player player) {
		List<String> list = Arrays.asList("Open Quests", "Completed Quests", "Production", "Bank Statement");
		player.addEvent(new ProgressStatusEvent(list));
		player.notifyWorldAboutChanges();
	}

	/**
	 * sends a list of items in the specified progress types
	 *
	 * @param player Player to sent the event to
	 * @param progressType the type of progress the player is interested in
	 */
	private void sendItemList(Player player, String progressType) {
		if (progressType.equals("Open Quests")) {
			player.addEvent(new ProgressStatusEvent(progressType,
					SingletonRepository.getStendhalQuestSystem().getOpenQuests(player)));
		} else if (progressType.equals("Completed Quests")) {
			// Send first the list of the quests that can be repeated
			player.addEvent(new ProgressStatusEvent("repeatable",
					SingletonRepository.getStendhalQuestSystem().getRepeatableQuests(player)));
			player.addEvent(new ProgressStatusEvent(progressType,
					SingletonRepository.getStendhalQuestSystem().getCompletedQuests(player)));
		} else if (progressType.equals("Production")) {
			player.addEvent(new ProgressStatusEvent(progressType,
					SingletonRepository.getProducerRegister().getWorkingProducerNames(player)));
		} else if (progressType.equals("Bank Statement")) {
			player.addEvent(new ProgressStatusEvent(progressType, Arrays.asList("All Banks")));
			
		}
		player.notifyWorldAboutChanges();
	}
	
	/**
	 * sends details about a selected item
	 *
	 * @param player Player to sent the event to
	 * @param progressType the type of progress the player is interested in
	 * @param item name of item to get details about
	 */
	private void sendDetails(Player player, String progressType, String item) {
		StendhalQuestSystem questSystem = SingletonRepository.getStendhalQuestSystem();
		if (progressType.equals("Open Quests") || progressType.equals("Completed Quests")) {
			player.addEvent(new ProgressStatusEvent(progressType, item,
					questSystem.getQuestDescription(player, item),
					questSystem.getQuestLevelWarning(player, item),
					questSystem.getQuestProgressDetails(player, item)));
		} else if (progressType.equals("Production")) {
			player.addEvent(new ProgressStatusEvent(progressType, item,
					SingletonRepository.getProducerRegister().getProductionDescription(player, item),
					SingletonRepository.getProducerRegister().getProductionDetails(player, item)));
		}  
		else if (progressType.equals("Bank Statement")) {
			
			 List<String> itemsFromAllBanks = new ArrayList<String>();
			 BankStatement bank_statement = new BankStatement(player);
			 // cycles through every bank
			 for (Banks bank_name: Banks.values()) {
				List <String> itemsFromBank = new ArrayList<String>();
				// gets every item from a bank
				itemsFromBank = bank_statement.getItemsFromBank(bank_name);
				if (!itemsFromBank.isEmpty()) {
					// if it is not empty, adds it to a string of items
					String stringToAdd = bank_name.getSlotName() + ": ";
					for (String item_name: itemsFromBank)
						stringToAdd += item_name + ", ";
					// removes extra comma at the end
					stringToAdd = stringToAdd.substring(0, stringToAdd.length() - 2);
					itemsFromAllBanks.add(stringToAdd);
				}
			 }
		
			 player.addEvent(new ProgressStatusEvent(progressType, item, "Items from all banks", itemsFromAllBanks));
			
		}
		player.notifyWorldAboutChanges();
	
	}
}

