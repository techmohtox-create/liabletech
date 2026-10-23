package com.liabletech.freight.pod;

import java.io.IOException;
import java.time.Clock;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.liabletech.freight.fleet.Fleet;
import com.liabletech.freight.fleet.Vehicle;
import com.liabletech.freight.party.Parties;
import com.liabletech.freight.trip.Trip;
import com.liabletech.freight.trip.Trips;
import com.liabletech.platform.ai.DocumentAi;
import com.liabletech.platform.files.FileStore;
import com.liabletech.platform.files.StoredFile;
import com.liabletech.platform.security.CurrentUser;

@Controller
@RequestMapping("/pods")
public class PodController {

	private final Pods pods;

	private final PodService podService;

	private final Trips trips;

	private final Parties parties;

	private final Fleet fleet;

	private final FileStore files;

	private final DocumentAi ai;

	private final Clock clock;

	public PodController(Pods pods, PodService podService, Trips trips, Parties parties, Fleet fleet, FileStore files,
			DocumentAi ai, Clock clock) {
		this.pods = pods;
		this.podService = podService;
		this.trips = trips;
		this.parties = parties;
		this.fleet = fleet;
		this.files = files;
		this.ai = ai;
		this.clock = clock;
	}

	@GetMapping
	public String inbox(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "review") String show,
			Model model) {
		String status = "all".equals(show) ? null : "review".equals(show) ? Pod.NEEDS_REVIEW : Pod.CONFIRMED;
		model.addAttribute("show", show);
		model.addAttribute("pods", pods.list(user.accountId(), status, 200));
		model.addAttribute("aiEnabled", ai.available());
		return "freight/pods";
	}

	@PostMapping("/upload")
	public String upload(@AuthenticationPrincipal CurrentUser user, @RequestParam MultipartFile file,
			@RequestParam(required = false) String caption, RedirectAttributes redirect) throws IOException {
		if (file.isEmpty()) {
			redirect.addFlashAttribute("error", "Choose a photo or PDF of the POD.");
			return "redirect:/pods";
		}
		StoredFile stored = files.save(user.accountId(), file.getBytes(), file.getContentType(), file.getOriginalFilename());
		PodService.Received received = podService.receive(user.accountId(), stored, null, "UPLOAD", caption);
		return "redirect:/pods/" + received.pod().id();
	}

	@GetMapping("/{id}")
	public String review(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
		Pod pod = pods.get(user.accountId(), id);
		Trip linked = trips.find(user.accountId(), pod.tripId()).orElse(null);
		PodReviewForm form = PodReviewForm.of(pod);
		if (linked != null) {
			form.fillBlanksFrom(linked, fleet.vehicle(user.accountId(), linked.vehicleId()).map(Vehicle::registration).orElse(null));
		}
		if (form.getDeliveryDate() == null && Pod.NEEDS_REVIEW.equals(pod.reviewStatus())) {
			form.setDeliveryDate(pod.receivedAt().atZoneSameInstant(clock.getZone()).toLocalDate());
		}
		model.addAttribute("pod", pod);
		model.addAttribute("form", form);
		model.addAttribute("file", files.get(pod.fileId()));
		List<Trip> suggestions = trips.matchingLr(user.accountId(), pod.lrNumber());
		model.addAttribute("suggestions", suggestions);
		model.addAttribute("openTrips", trips.list(user.accountId(), Trip.IN_TRANSIT, 100));
		model.addAttribute("linkedTrip", linked);
		model.addAttribute("parties", parties.all(user.accountId()));
		model.addAttribute("driver", pod.driverId() == null ? null : fleet.driver(user.accountId(), pod.driverId()));
		return "freight/pod-review";
	}

	@PostMapping("/{id}/confirm")
	public String confirm(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@ModelAttribute PodReviewForm form, RedirectAttributes redirect) {
		long tripId = podService.confirm(user.accountId(), id, form);
		redirect.addFlashAttribute("notice", "POD confirmed. The trip is ready to bill.");
		return "redirect:/trips/" + tripId;
	}

	@PostMapping("/{id}/reject")
	public String reject(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, RedirectAttributes redirect) {
		podService.reject(user.accountId(), id);
		redirect.addFlashAttribute("notice", "Marked as not a POD.");
		return "redirect:/pods";
	}

}
