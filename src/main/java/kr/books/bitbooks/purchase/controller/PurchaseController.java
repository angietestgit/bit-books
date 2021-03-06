package kr.books.bitbooks.purchase.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.util.IOUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import kr.books.bitbooks.common.vo.PagingVO;
import kr.books.bitbooks.member.service.MemberService;
import kr.books.bitbooks.member.vo.MemberVO;
import kr.books.bitbooks.purchase.service.PurchaseService;
import kr.books.bitbooks.purchase.vo.PurchaseVO;

@Controller
@RequiredArgsConstructor
public class PurchaseController {

	private final MemberService service;

	private final PurchaseService pservice;

	private final AmazonS3 amazonS3;

	@Value("${cloud.aws.s3.bucket}")
	private String bucket;

	@PostMapping("/purchase/buy")
	@ResponseBody
	public Map<String, Object> PurchaseControl(@ModelAttribute PurchaseVO purchaseRequest, HttpServletRequest request) {

		Map<String, Object> resultMap = new HashMap<>();
		int resultCode = 200;
		String resultMsg = "";
		
		HttpSession session = request.getSession();
		if (session == null) {
			resultCode = 400;
			resultMsg = "????????? ????????????. ";

			resultMap.put("resultCode", resultCode);
			resultMap.put("resultMsg", resultMsg);

			return resultMap;
		}

		MemberVO vo = (MemberVO) session.getAttribute("userInfo");
		if (vo == null) {
			resultCode = 400;
			resultMsg = "????????? ??? ??????????????????. ";

			resultMap.put("resultCode", resultCode);
			resultMap.put("resultMsg", resultMsg);

			return resultMap;
		}

		//System.out.println("userNo : " + vo.getUserNo());
		//System.out.println("itemId : " + purchaseRequest.getItemId());
		//System.out.println("itemName : " + purchaseRequest.getItemName());
		//System.out.println("itemprice : " + purchaseRequest.getItemPrice());

		try {
			int coin = service.getMemberCoin(vo.getUserNo());

			if (coin >= purchaseRequest.getItemPrice()) {
				System.out.println("?????? ??????");
				coin = coin - purchaseRequest.getItemPrice();
				System.out.println(coin);

				purchaseRequest.setUserNo(vo.getUserNo());

				pservice.insertPurchase(purchaseRequest);

				vo.setCoin(coin);
				service.updateCoin(vo);

				resultMsg = "OK";
			} else {
				System.out.println("????????? ???????????????.");
				resultCode = 400;
				resultMsg = "????????? ???????????????.";
			}
		} catch (Exception e) {
			e.printStackTrace();
			resultCode = 400;
			resultMsg = e.getLocalizedMessage();
		}

		resultMap.put("resultCode", resultCode);
		resultMap.put("resultMsg", resultMsg);

		return resultMap;
	}

	@GetMapping("/purchase/log")
	public ModelAndView PurchaseLog(@RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
			HttpServletRequest request) {

		ModelAndView view = new ModelAndView();
		view.setViewName("views/purchase/buy_log");

		try {
			HttpSession session = request.getSession();
			if (session == null) {
				view.setViewName("views/login/login");
				return view;
			}

			MemberVO vo = (MemberVO) session.getAttribute("userInfo");
			if (vo == null) {
				view.setViewName("views/login/login");
				return view;
			}

			// ????????? ?????? ?????? ??????
			PagingVO pagingVO = new PagingVO();

			System.out.println("getUserNo:" + vo.getUserNo());

			// ????????? ????????? ????????? ??? ??????
			int totalCount = pservice.getPurchaseCount(vo.getUserNo());

			// ?????? ?????? ????????????
			pagingVO.setTotalCount(totalCount);

			// ?????? ????????? ????????????
			pagingVO.setCurrentPage(currentPage);
			System.out.println("currentPage:" + currentPage);
			System.out.println("totalCount:" + totalCount);

			// ????????? ??????????????? ????????? ????????? ?????? ????????? ????????????
			List<PurchaseVO> PurchaseList = pservice.getPurchaseList(vo.getUserNo(), pagingVO);
			System.out.println("PurchaseList:" + PurchaseList.size());

			for (int i = 0; i < PurchaseList.size(); i++) {
				PurchaseList.get(i).setPurchaseNo(i + 1 + (currentPage - 1) * pagingVO.getCountPerPage());
			}

			// view ??? ???????????? ????????? ????????? ??????
			view.addObject("currentPage", currentPage);
			view.addObject("paging", pagingVO);
			view.addObject("totalCount", totalCount);
			view.addObject("dataList", PurchaseList);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return view;
	}

	@GetMapping("/down/file")
	public ResponseEntity<byte[]> getObject(@RequestParam(value = "fileName") String storedFileName)
			throws IOException {
		S3Object o = amazonS3.getObject(new GetObjectRequest(bucket + "/books", storedFileName));
		S3ObjectInputStream objectInputStream = o.getObjectContent();
		byte[] bytes = IOUtils.toByteArray(objectInputStream);

		String fileName = URLEncoder.encode(storedFileName, "UTF-8").replaceAll("\\+", "%20");
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		httpHeaders.setContentLength(bytes.length);
		httpHeaders.setContentDispositionFormData("attachment", fileName);

		return new ResponseEntity<>(bytes, httpHeaders, HttpStatus.OK);
	}

}
